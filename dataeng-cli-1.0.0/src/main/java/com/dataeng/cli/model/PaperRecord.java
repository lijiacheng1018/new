package com.dataeng.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一论文/文献记录模型。
 * 字段类型与 JSON 输出中的键名保持稳定，供 validate 做 schema 校验。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "title", "authors", "published", "updated", "summary",
        "primary_category", "categories", "doi", "link"})
public class PaperRecord {

    /** 唯一 ID（arXiv 编号 / mock 编号） */
    private String id;

    /** 标题 */
    private String title;

    /** 作者列表 */
    private List<String> authors = new ArrayList<>();

    /** 发布时间（ISO-8601，如 2024-01-15T08:30:00Z） */
    private String published;

    /** 更新时间（ISO-8601） */
    private String updated;

    /** 摘要 */
    private String summary;

    /** 主分类 */
    private String primaryCategory;

    /** 全部分类 */
    private List<String> categories = new ArrayList<>();

    /** DOI（若存在） */
    private String doi;

    /** 原文链接 */
    private String link;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors == null ? new ArrayList<>() : authors;
    }

    public String getPublished() {
        return published;
    }

    public void setPublished(String published) {
        this.published = published;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPrimaryCategory() {
        return primaryCategory;
    }

    public void setPrimaryCategory(String primaryCategory) {
        this.primaryCategory = primaryCategory;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories == null ? new ArrayList<>() : categories;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    /** 解析后的发布时间（不可解析时返回 null，用于排序与 watermark 推进） */
    @JsonIgnore
    public LocalDateTime getPublishedTime() {
        if (published == null) {
            return null;
        }
        try {
            return com.dataeng.cli.util.DateUtil.parse(published);
        } catch (Exception e) {
            return null;
        }
    }
}
