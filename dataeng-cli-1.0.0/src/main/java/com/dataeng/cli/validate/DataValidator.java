package com.dataeng.cli.validate;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.model.QualityReport;
import com.dataeng.cli.store.StorageManager;
import com.dataeng.cli.util.DateUtil;
import com.dataeng.cli.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据质量校验器。
 *
 * 校验项：
 *  1. 必填字段完整率（id/title/published/authors/summary）
 *  2. 唯一 ID 重复率
 *  3. schema 字段类型 / 格式（字符串非空、数组元素、日期可解析、ID 非空）
 *  4. 过期记录（published 超过 5 年，WARN 级）
 *
 * 兼容性：空目录 / 不存在目录 / 非法 JSON 文件均有明确处理，不抛异常。
 */
public class DataValidator {

    public QualityReport validate(Path dataDir) {
        QualityReport report = new QualityReport();
        report.getChecks().add("必填字段完整率");
        report.getChecks().add("唯一 ID 重复率");
        report.getChecks().add("schema 字段类型/格式");
        report.getChecks().add("过期记录");

        List<Path> files = StorageManager.listJsonFiles(dataDir);
        if (!Files.exists(dataDir)) {
            report.setPass(false);
            report.setComment("校验失败：目录不存在 " + dataDir.toAbsolutePath());
            return report;
        }
        if (files.isEmpty()) {
            report.setPass(false);
            report.setComment("校验失败：目录中没有任何 JSON 文件（空目录或目录下无采集结果）");
            return report;
        }

        List<PaperRecord> records = loadRecords(files, report);

        if (records.isEmpty()) {
            report.setPass(false);
            report.setComment("校验失败：JSON 文件均无法解析出有效记录");
            return report;
        }

        int total = records.size();
        report.setTotalRecords(total);

        // 1) 必填字段完整率
        Map<String, Integer> present = new LinkedHashMap<>();
        for (String f : SchemaDefinition.REQUIRED_FIELDS) {
            present.put(f, 0);
        }
        for (PaperRecord r : records) {
            if (isPresent(r.getId())) present.put("id", present.get("id") + 1);
            if (isPresent(r.getTitle())) present.put("title", present.get("title") + 1);
            if (isPresent(r.getPublished())) present.put("published", present.get("published") + 1);
            if (r.getAuthors() != null && !r.getAuthors().isEmpty()) present.put("authors", present.get("authors") + 1);
            if (isPresent(r.getSummary())) present.put("summary", present.get("summary") + 1);
        }
        double minRate = 1.0;
        boolean completenessOk = true;
        for (String f : SchemaDefinition.REQUIRED_FIELDS) {
            double rate = round3((double) present.get(f) / total);
            report.getFieldCompleteness().put(f, rate);
            if (rate < SchemaDefinition.MIN_COMPLETENESS) {
                completenessOk = false;
                int missing = total - present.get(f);
                report.addIssue(new QualityReport.Issue("ERROR", "必填字段完整率", f,
                        "完整率 " + pct(rate) + "，缺失 " + missing + " 条"));
            }
            minRate = Math.min(minRate, rate);
        }
        // id 缺失属于致命问题
        if (present.get("id") < total) {
            report.addIssue(new QualityReport.Issue("ERROR", "必填字段完整率", "id",
                    "存在缺失 id 的记录，无法保证唯一性"));
        }

        // 2) 唯一 ID 重复率
        Set<String> seen = new HashSet<>();
        Set<String> dups = new HashSet<>();
        for (PaperRecord r : records) {
            if (r.getId() == null || r.getId().trim().isEmpty()) {
                continue;
            }
            if (!seen.add(r.getId())) {
                dups.add(r.getId());
            }
        }
        int dupCount = dups.size();
        double dupRate = round3((double) dupCount / total);
        report.setDuplicateCount(dupCount);
        report.setDuplicateRate(dupRate);
        if (dupRate > SchemaDefinition.MAX_DUPLICATE_RATE) {
            report.addIssue(new QualityReport.Issue("ERROR", "唯一 ID 重复率", "id",
                    "重复率 " + pct(dupRate) + " 超过阈值 " + pct(SchemaDefinition.MAX_DUPLICATE_RATE)
                            + "，重复 ID 示例: " + String.join(", ", dups)));
        }

        // 3) schema 字段类型 / 格式
        int schemaErrors = checkSchema(records, report);

        // 4) 过期记录（WARN）
        int staleCount = 0;
        for (PaperRecord r : records) {
            if (r.getPublished() != null && DateUtil.isStale(r.getPublished(), SchemaDefinition.STALE_YEARS)) {
                staleCount++;
            }
        }
        if (staleCount > 0) {
            report.addIssue(new QualityReport.Issue("WARN", "过期记录", "published",
                    staleCount + " 条记录发布时间超过 " + SchemaDefinition.STALE_YEARS + " 年"));
        }

        // 5) 汇总判定
        boolean pass = completenessOk && dupRate <= SchemaDefinition.MAX_DUPLICATE_RATE && schemaErrors == 0;
        report.setPass(pass);

        StringBuilder comment = new StringBuilder();
        comment.append(pass ? "通过" : "未通过")
                .append("：记录数 ").append(total)
                .append("，必填字段完整率最低 ").append(pct(minRate))
                .append("，重复率 ").append(pct(dupRate))
                .append("，schema 类型/格式错误 ").append(schemaErrors)
                .append("，过期记录 ").append(staleCount);
        report.setComment(comment.toString());
        return report;
    }

    private int checkSchema(List<PaperRecord> records, QualityReport report) {
        int errors = 0;
        for (int i = 0; i < records.size(); i++) {
            PaperRecord r = records.get(i);
            String where = "第 " + (i + 1) + " 条";

            for (String f : SchemaDefinition.STRING_FIELDS) {
                String v = stringField(r, f);
                if (v == null) {
                    continue; // 允许为空的可选字符串字段
                }
                if (v.isEmpty()) {
                    errors++;
                    report.addIssue(new QualityReport.Issue("ERROR", "schema 字段类型/格式", f,
                            where + " 的 " + f + " 为空白字符串"));
                }
                if ("published".equals(f) || "updated".equals(f)) {
                    if (!DateUtil.isParseable(v)) {
                        errors++;
                        report.addIssue(new QualityReport.Issue("ERROR", "schema 字段类型/格式", f,
                                where + " 的 " + f + " 不是合法日期: '" + v + "'"));
                    }
                }
            }

            // ID 格式：非空且不含空白
            if (r.getId() != null && r.getId().trim().isEmpty()) {
                errors++;
                report.addIssue(new QualityReport.Issue("ERROR", "schema 字段类型/格式", "id",
                        where + " 的 id 为空白"));
            }

            // 数组字段
            for (String f : SchemaDefinition.ARRAY_STRING_FIELDS) {
                List<String> arr = arrayField(r, f);
                if (arr == null) {
                    continue;
                }
                for (int j = 0; j < arr.size(); j++) {
                    if (arr.get(j) == null || arr.get(j).trim().isEmpty()) {
                        errors++;
                        report.addIssue(new QualityReport.Issue("ERROR", "schema 字段类型/格式", f,
                                where + " 的 " + f + "[" + j + "] 为空白元素"));
                    }
                }
            }
        }
        return errors;
    }

    private List<PaperRecord> loadRecords(List<Path> files, QualityReport report) {
        List<PaperRecord> records = new ArrayList<>();
        for (Path f : files) {
            JsonNode node;
            try {
                byte[] bytes = Files.readAllBytes(f);
                node = JsonUtil.readTree(bytes);
            } catch (DataEngException e) {
                report.addIssue(new QualityReport.Issue("WARN", "文件解析", f.toString(),
                        "文件不是合法 JSON，已跳过: " + e.getMessage()));
                continue;
            } catch (Exception e) {
                report.addIssue(new QualityReport.Issue("WARN", "文件解析", f.toString(),
                        "读取失败，已跳过: " + e.getMessage()));
                continue;
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    try {
                        records.add(JsonUtil.mapper().treeToValue(item, PaperRecord.class));
                    } catch (Exception e) {
                        report.addIssue(new QualityReport.Issue("WARN", "文件解析", f.toString(),
                                "数组内存在无法反序列化的记录，已跳过: " + e.getMessage()));
                    }
                }
            } else if (node.isObject()) {
                try {
                    records.add(JsonUtil.mapper().treeToValue(node, PaperRecord.class));
                } catch (Exception e) {
                    report.addIssue(new QualityReport.Issue("WARN", "文件解析", f.toString(),
                            "对象无法反序列化，已跳过: " + e.getMessage()));
                }
            } else {
                report.addIssue(new QualityReport.Issue("WARN", "文件解析", f.toString(),
                        "顶层既不是数组也不是对象，已跳过"));
            }
        }
        return records;
    }

    private static boolean isPresent(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String stringField(PaperRecord r, String name) {
        switch (name) {
            case "id": return r.getId();
            case "title": return r.getTitle();
            case "published": return r.getPublished();
            case "updated": return r.getUpdated();
            case "summary": return r.getSummary();
            case "primary_category": return r.getPrimaryCategory();
            case "doi": return r.getDoi();
            case "link": return r.getLink();
            default: return null;
        }
    }

    private static List<String> arrayField(PaperRecord r, String name) {
        switch (name) {
            case "authors": return r.getAuthors();
            case "categories": return r.getCategories();
            default: return null;
        }
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }
}
