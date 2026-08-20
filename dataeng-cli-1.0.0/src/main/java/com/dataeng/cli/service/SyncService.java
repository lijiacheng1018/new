package com.dataeng.cli.service;

import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.model.SyncResult;
import com.dataeng.cli.source.DataSource;
import com.dataeng.cli.source.SearchResponse;
import com.dataeng.cli.store.StorageManager;
import com.dataeng.cli.store.WatermarkManager;
import com.dataeng.cli.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 增量同步服务：watermark 游标 + 唯一 ID 幂等去重。
 *
 * 流程：
 *  1. 起点 since = 显式 --since > DB watermark > 默认近 30 天；
 *  2. 拉取 [since, now] 窗口（query 为空，仅时间区间）；
 *  3. 逐条按 (source, id) 判定 NEW / UPDATED / SKIPPED，内容哈希用于识别更新；
 *  4. watermark 推进到 max(已见记录发布时间, now)，保证下次不会重复拉同一窗口。
 */
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final int DEFAULT_SINCE_DAYS = 30;

    private final DataSource source;
    private final StorageManager storage;
    private final WatermarkManager wm;

    public SyncService(DataSource source, StorageManager storage, WatermarkManager wm) {
        this.source = source;
        this.storage = storage;
        this.wm = wm;
    }

    public SyncResult run(String explicitSince, int maxResults) {
        return run(explicitSince, DateUtil.nowUtc(), maxResults);
    }

    /** 供测试注入固定 to 的重载 */
    SyncResult run(String explicitSince, LocalDateTime to, int maxResults) {
        long t0 = System.currentTimeMillis();
        LocalDateTime since = resolveSince(explicitSince);
        LocalDateTime until = to;

        SearchResponse resp = source.search(null, since, until, maxResults);
        List<PaperRecord> records = resp.getRecords();

        int added = 0;
        int updated = 0;
        int skipped = 0;
        LocalDateTime maxPublished = since;

        for (PaperRecord r : records) {
            String hash = contentHash(r);
            WatermarkManager.Decision d = wm.classify(source.name(), r.getId(), hash);
            switch (d) {
                case NEW:
                    wm.upsertSeen(source.name(), r.getId(), hash);
                    added++;
                    break;
                case UPDATED:
                    wm.upsertSeen(source.name(), r.getId(), hash);
                    updated++;
                    break;
                default:
                    skipped++;
            }
            LocalDateTime pub = r.getPublishedTime();
            if (pub != null && pub.isAfter(maxPublished)) {
                maxPublished = pub;
            }
        }

        // 增量结果落盘（原始响应 + 处理后的 JSON），便于后续 validate
        if (!records.isEmpty()) {
            storage.saveRaw(source.name(), "sync[" + DateUtil.toIso(since) + " TO " + DateUtil.toIso(until) + "]", resp.getRawBody());
            storage.saveProcessed(source.name(), records);
        }

        // watermark 推进：有记录则取最大发布时间，否则推进到窗口上界，避免空转
        String newWatermark = maxPublished.isAfter(since)
                ? DateUtil.toIso(maxPublished)
                : DateUtil.toIso(until);
        wm.setWatermark(source.name(), newWatermark);

        SyncResult result = new SyncResult();
        result.setSource(source.name());
        result.setSince(DateUtil.toIso(since));
        result.setUntil(DateUtil.toIso(until));
        result.setFetched(records.size());
        result.setAdded(added);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        result.setTotalSeen(wm.seenCount(source.name()));
        result.setTookMs(System.currentTimeMillis() - t0);
        log.info("sync 完成: since={} until={} fetched={} added={} updated={} skipped={}",
                result.getSince(), result.getUntil(), result.getFetched(),
                result.getAdded(), result.getUpdated(), result.getSkipped());
        return result;
    }

    private LocalDateTime resolveSince(String explicitSince) {
        if (explicitSince != null && !explicitSince.trim().isEmpty()) {
            return DateUtil.parse(explicitSince);
        }
        Optional<String> wmSince = wm.getWatermark(source.name());
        if (wmSince.isPresent()) {
            try {
                return DateUtil.parse(wmSince.get());
            } catch (IllegalArgumentException e) {
                log.warn("watermark 值无法解析（{}），回退默认窗口", wmSince.get());
            }
        }
        return DateUtil.nowUtc().minusDays(DEFAULT_SINCE_DAYS);
    }

    /** 记录内容哈希：ID + 标题 + 摘要 + 作者 + 发布时间，用于识别"更新"。 */
    static String contentHash(PaperRecord r) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(r.getId()).append('|')
                    .append(nullToEmpty(r.getTitle())).append('|')
                    .append(nullToEmpty(r.getSummary())).append('|')
                    .append(nullToEmpty(r.getPublished())).append('|')
                    .append(r.getAuthors() == null ? "" : String.join(",", r.getAuthors()));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            // 极端情况下退化为 id 哈希
            return String.valueOf(r.getId().hashCode());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
