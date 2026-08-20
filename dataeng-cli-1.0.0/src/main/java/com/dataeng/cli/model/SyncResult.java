package com.dataeng.cli.model;

/**
 * sync 命令结果：新增 / 更新 / 跳过 三组计数 + watermark 游标推进信息。
 */
public class SyncResult {

    private String source;
    private String since;
    private String until;
    private int fetched;
    private int added;
    private int updated;
    private int skipped;
    private int totalSeen;
    private long tookMs;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSince() {
        return since;
    }

    public void setSince(String since) {
        this.since = since;
    }

    public String getUntil() {
        return until;
    }

    public void setUntil(String until) {
        this.until = until;
    }

    public int getFetched() {
        return fetched;
    }

    public void setFetched(int fetched) {
        this.fetched = fetched;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getTotalSeen() {
        return totalSeen;
    }

    public void setTotalSeen(int totalSeen) {
        this.totalSeen = totalSeen;
    }

    public long getTookMs() {
        return tookMs;
    }

    public void setTookMs(long tookMs) {
        this.tookMs = tookMs;
    }
}
