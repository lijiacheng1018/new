package com.dataeng.cli.model;

/**
 * fetch 命令结果：包含拉取数量与落盘文件位置。
 */
public class FetchResult {

    private String source;
    private String query;
    private int count;
    private String rawFile;
    private String processedFile;
    private long tookMs;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getRawFile() {
        return rawFile;
    }

    public void setRawFile(String rawFile) {
        this.rawFile = rawFile;
    }

    public String getProcessedFile() {
        return processedFile;
    }

    public void setProcessedFile(String processedFile) {
        this.processedFile = processedFile;
    }

    public long getTookMs() {
        return tookMs;
    }

    public void setTookMs(long tookMs) {
        this.tookMs = tookMs;
    }
}
