package com.dataeng.cli.validate;

import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.model.QualityReport;
import com.dataeng.cli.store.StorageManager;
import com.dataeng.cli.util.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataValidatorTest {

    @TempDir
    Path tmp;

    private PaperRecord cleanRecord(String id, String title) {
        PaperRecord r = new PaperRecord();
        r.setId(id);
        r.setTitle(title);
        r.setPublished("2024-01-15T08:30:00Z");
        r.setUpdated("2024-01-15T08:30:00Z");
        r.setSummary("A summary.");
        r.setAuthors(Arrays.asList("Alice", "Bob"));
        r.setCategories(Collections.singletonList("cs.DC"));
        r.setPrimaryCategory("cs.DC");
        return r;
    }

    @Test
    void cleanDataPasses() throws Exception {
        Path dir = tmp.resolve("clean");
        Files.createDirectories(dir);
        Path f = dir.resolve("20240101.json");
        JsonUtil.writeToFile(f, Arrays.asList(
                cleanRecord("2401.00001", "Paper A"),
                cleanRecord("2401.00002", "Paper B")));

        QualityReport report = new DataValidator().validate(dir);
        assertTrue(report.isPass(), "干净数据应通过: " + report.getComment());
        assertTrue(report.getIssues().isEmpty(), "不应有问题: " + report.getIssues());
    }

    @Test
    void dirtyDataFails() throws Exception {
        Path dir = tmp.resolve("dirty");
        Files.createDirectories(dir);

        PaperRecord missingTitle = cleanRecord("2401.00001", "OK");
        missingTitle.setTitle(null);

        PaperRecord badDate = cleanRecord("2401.00002", "Bad date");
        badDate.setPublished("not-a-date");

        PaperRecord dup = cleanRecord("2401.00003", "Dup");
        dup.setId("2401.00001"); // 与第一条重复

        JsonUtil.writeToFile(dir.resolve("d.json"), Arrays.asList(missingTitle, badDate, dup));

        QualityReport report = new DataValidator().validate(dir);
        assertFalse(report.isPass(), "脏数据应判 FAIL: " + report.getComment());
        assertTrue(report.getDuplicateCount() >= 1);
        boolean hasDateIssue = report.getIssues().stream()
                .anyMatch(i -> "published".equals(i.getField()) && "ERROR".equals(i.getLevel()));
        assertTrue(hasDateIssue, "应报出非法日期问题");
    }

    @Test
    void emptyDirReportsFailWithoutCrash() {
        Path dir = tmp.resolve("empty");
        QualityReport report = new DataValidator().validate(dir);
        assertFalse(report.isPass());
        assertTrue(report.getComment().contains("不存在") || report.getComment().contains("没有"));
    }

    @Test
    void invalidJsonFileIsSkipped() throws Exception {
        Path dir = tmp.resolve("bad");
        Files.createDirectories(dir);
        Files.write(dir.resolve("bad.json"), "this is not json{{{".getBytes("UTF-8"));

        QualityReport report = new DataValidator().validate(dir);
        assertFalse(report.isPass());
        assertTrue(report.getComment().contains("无法解析"));
    }

    @Test
    void rawDirDuplicatesAreNotCountedTwice() throws Exception {
        // 模拟 fetch 产物：processed 与 raw 各有一份相同记录。
        // validate 只应统计 processed，否则 raw 会把每条记录数两遍 → 重复率虚高。
        Path raw = tmp.resolve("raw/mock");
        Path processed = tmp.resolve("processed/mock/2024/01");
        Files.createDirectories(raw);
        Files.createDirectories(processed);

        java.util.List<PaperRecord> records = Arrays.asList(
                cleanRecord("2401.00001", "Paper A"),
                cleanRecord("2401.00002", "Paper B"),
                cleanRecord("2401.00003", "Paper C"));

        JsonUtil.writeToFile(raw.resolve("raw-20240115.json"), records);
        JsonUtil.writeToFile(processed.resolve("20240115.json"), records);

        QualityReport report = new DataValidator().validate(tmp);
        assertTrue(report.isPass(), "raw 与 processed 重复不应导致误报: " + report.getComment());
        assertTrue(report.getTotalRecords() == 3, "应只统计 processed 的 3 条记录");
        assertTrue(report.getDuplicateCount() == 0, "不应报出重复: " + report.getIssues());
    }
}
