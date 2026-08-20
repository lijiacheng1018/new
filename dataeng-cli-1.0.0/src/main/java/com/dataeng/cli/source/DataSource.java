package com.dataeng.cli.source;

import java.time.LocalDateTime;

/**
 * 数据源抽象。
 * 统一返回 {@link SearchResponse}（原始响应 + 解析后记录）。
 *
 * @param query 查询关键词/ID；可为 null 表示仅按时间范围拉取（sync 场景）
 * @param from  起始时间（含），可为 null 表示不限制
 * @param to    结束时间（含），可为 null 表示不限制
 */
public interface DataSource {

    /** 数据源名称：arxiv | mock */
    String name();

    /** 是否为离线模拟源 */
    boolean isMock();

    SearchResponse search(String query, LocalDateTime from, LocalDateTime to, int maxResults);
}
