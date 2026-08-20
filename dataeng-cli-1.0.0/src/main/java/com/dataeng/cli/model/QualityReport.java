package com.dataeng.cli.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量校验报告：总体 pass/comment + 各项指标 + 明细问题列表。
 */
@JsonPropertyOrder({"pass", "comment", "totalRecords", "duplicateCount", "duplicateRate",
        "fieldCompleteness", "checks", "issues"})
public class QualityReport {

    /** 是否整体通过 */
    private boolean pass;

    /** 总结论（人读） */
    private String comment;

    /** 参与校验的记录总数 */
    private int totalRecords;

    /** 重复记录数（按唯一 ID） */
    private int duplicateCount;

    /** 重复率 = duplicateCount / totalRecords */
    private double duplicateRate;

    /** 必填字段完整率: 字段名 -> 完整率(0~1) */
    private Map<String, Double> fieldCompleteness = new LinkedHashMap<>();

    /** 已执行的校验项列表 */
    private List<String> checks = new ArrayList<>();

    /** 明细问题 */
    private List<Issue> issues = new ArrayList<>();

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public double getDuplicateRate() {
        return duplicateRate;
    }

    public void setDuplicateRate(double duplicateRate) {
        this.duplicateRate = duplicateRate;
    }

    public Map<String, Double> getFieldCompleteness() {
        return fieldCompleteness;
    }

    public void setFieldCompleteness(Map<String, Double> fieldCompleteness) {
        this.fieldCompleteness = fieldCompleteness;
    }

    public List<String> getChecks() {
        return checks;
    }

    public void setChecks(List<String> checks) {
        this.checks = checks;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public void setIssues(List<Issue> issues) {
        this.issues = issues;
    }

    public void addIssue(Issue issue) {
        issues.add(issue);
    }

    /** 校验问题明细 */
    @JsonPropertyOrder({"level", "check", "field", "message"})
    public static class Issue {

        /** ERROR | WARN */
        private String level;
        private String check;
        private String field;
        private String message;

        public Issue() {
        }

        public Issue(String level, String check, String field, String message) {
            this.level = level;
            this.check = check;
            this.field = field;
            this.message = message;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getCheck() {
            return check;
        }

        public void setCheck(String check) {
            this.check = check;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
