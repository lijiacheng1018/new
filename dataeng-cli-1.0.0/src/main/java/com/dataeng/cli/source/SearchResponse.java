package com.dataeng.cli.source;

import com.dataeng.cli.model.PaperRecord;

import java.util.List;

/**
 * 一次搜索的原始响应 + 解析后的记录列表。
 * rawBody 为数据源返回的原始响应（arXiv 为 Atom XML，mock 为 JSON），
 * 供 fetch/sync 落盘"原始响应"。
 */
public class SearchResponse {

    private final String rawBody;
    private final List<PaperRecord> records;

    public SearchResponse(String rawBody, List<PaperRecord> records) {
        this.rawBody = rawBody;
        this.records = records;
    }

    public String getRawBody() {
        return rawBody;
    }

    public List<PaperRecord> getRecords() {
        return records;
    }
}
