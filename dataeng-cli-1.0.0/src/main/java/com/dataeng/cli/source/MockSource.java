package com.dataeng.cli.source;

import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.util.DateUtil;
import com.dataeng.cli.util.JsonUtil;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 离线模拟数据源：确定性（相同参数产生相同结果），支持脏数据注入，
 * 用于无网络环境的可复现演示与自动化测试。
 *
 * 幂等语义设计（对齐真实 arXiv 行为）：
 *  - 记录 ID 只依赖 (query, 窗口起点 from)，与窗口上界无关
 *    → 同一起点重跑同一窗口，记录 ID 稳定 → 可稳定命中"跳过/更新"去重；
 *  - 发布时间 = 起点 + i*3 天，超过窗口上界时钳制到上界附近
 *    → 窗口上界推进时，尾部记录发布时间变化 → 内容哈希变化 → 可演示"更新"；
 *  - dirty 模式注入：空标题、非法日期、重复 ID，供 validate 展示。
 */
public class MockSource implements DataSource {

    public static final String NAME = "mock";

    private final boolean dirty;

    public MockSource() {
        this(false);
    }

    public MockSource(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public SearchResponse search(String query, LocalDateTime from, LocalDateTime to, int maxResults) {
        String q = (query == null || query.trim().isEmpty()) ? "sync" : query.trim();
        // 约定：包含 "no_such" / "noresult" 的查询返回空结果，
        // 用于离线演示"查询无结果"（NO_RESULTS）错误路径
        String lower = q.toLowerCase();
        if (lower.contains("no_such") || lower.contains("noresult")) {
            return new SearchResponse("[]", new ArrayList<>());
        }
        int seed = q.hashCode() ^ (from == null ? 0 : from.toLocalDate().hashCode());
        Random rnd = new Random(seed);

        LocalDateTime lo = from != null ? from : DateUtil.nowUtc().minusDays(30);
        LocalDateTime hi = to != null ? to : DateUtil.nowUtc();

        int n = Math.max(3, Math.min(maxResults, 5 + rnd.nextInt(6))); // 3..maxResults，确定性
        List<PaperRecord> records = new ArrayList<>();
        String seedTag = Integer.toHexString(seed & 0xffff);

        for (int i = 1; i <= n; i++) {
            PaperRecord r = new PaperRecord();
            r.setId(String.format("mock-%04d-%s", i, seedTag));
            r.setTitle(mockTitle(rnd, i, q));
            r.setSummary(mockSummary(rnd, i, q));
            LocalDateTime published = clamp(lo.plus(i * 3L, ChronoUnit.DAYS), lo, hi, i);
            r.setPublished(DateUtil.toIso(published));
            r.setUpdated(DateUtil.toIso(published.plusDays(1)));
            r.setAuthors(mockAuthors(rnd, i));
            r.setPrimaryCategory("cs.DC");
            List<String> cats = new ArrayList<>();
            cats.add("cs.DC");
            cats.add("cs.DS");
            r.setCategories(cats);
            r.setLink("https://example.org/abs/" + r.getId());
            r.setDoi(i % 3 == 0 ? "10.0000/mock." + seedTag + "." + i : null);
            records.add(r);
        }

        if (dirty && n >= 4) {
            // 注入脏数据（固定位置，便于断言）：
            records.get(n - 1).setTitle(null);                       // 1) 缺标题
            records.get(n - 2).setPublished("not-a-date");           // 2) 非法日期
            records.get(n - 3).setId(records.get(0).getId());        // 3) 与首条重复 ID
        }

        String rawBody = JsonUtil.toPrettyJson(records); // mock 的"原始响应"即 JSON
        return new SearchResponse(rawBody, records);
    }

    /**
     * 窗口内确定性时间：基准为 lo + i*3 天；超出 hi 时钳制到 hi 附近。
     * 同一 (lo, hi, i) 永远得到同一时间，保证可复现；hi 变化时尾部记录时间变化 → 触发"更新"。
     */
    private static LocalDateTime clamp(LocalDateTime base, LocalDateTime lo, LocalDateTime hi, int i) {
        if (!base.isAfter(hi)) {
            return base;
        }
        LocalDateTime clamped = hi.minusSeconds(i * 7L);
        return clamped.isBefore(lo) ? lo.plusSeconds(i) : clamped;
    }

    private static String mockTitle(Random rnd, int i, String query) {
        String[] nouns = {"Streaming", "Join", "Watermark", "Pipeline", "Indexing", "Federation",
                "Incremental", "Scheduling", "Fault Tolerance", "Vectorization"};
        String n = nouns[rnd.nextInt(nouns.length)];
        return "Towards Efficient " + n + " on " + query + " #" + i;
    }

    private static String mockSummary(Random rnd, int i, String query) {
        return "A deterministic mock paper about " + query
                + ", generated for offline demo and testing. (#" + i + ")";
    }

    private static List<String> mockAuthors(Random rnd, int i) {
        String[] names = {"Ada Lovelace", "Alan Turing", "Grace Hopper", "Edsger Dijkstra",
                "Barbara Liskov", "Donald Knuth"};
        List<String> authors = new ArrayList<>();
        authors.add(names[rnd.nextInt(names.length)]);
        authors.add(names[rnd.nextInt(names.length)]);
        return authors;
    }
}
