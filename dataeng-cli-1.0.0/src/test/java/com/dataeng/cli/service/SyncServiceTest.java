package com.dataeng.cli.service;

import com.dataeng.cli.model.SyncResult;
import com.dataeng.cli.source.MockSource;
import com.dataeng.cli.store.StorageManager;
import com.dataeng.cli.store.WatermarkManager;
import com.dataeng.cli.util.DateUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncServiceTest {

    @TempDir
    Path tmp;

    @Test
    void firstRunAddsSecondRunIdempotent() {
        String db = tmp.resolve("state.db").toString();
        LocalDateTime since = DateUtil.parse("2024-01-01");
        LocalDateTime to1 = DateUtil.parse("2024-01-10");
        LocalDateTime to2 = DateUtil.parse("2024-01-11");

        try (WatermarkManager wm = new WatermarkManager(db)) {
            SyncService service = new SyncService(new MockSource(false),
                    new StorageManager(tmp.toString()), wm);

            SyncResult first = service.run(DateUtil.toIso(since), to1, 20);
            assertTrue(first.getAdded() > 0, "首次应新增记录");
            assertEquals(0, first.getSkipped());

            // 相同窗口重跑 → 全部去重跳过（幂等）
            SyncResult second = service.run(DateUtil.toIso(since), to1, 20);
            assertEquals(first.getAdded(), second.getSkipped(), "重跑应全部命中去重");
            assertEquals(0, second.getAdded());
            assertEquals(0, second.getUpdated());

            // 窗口扩大（to2 > to1）→ ID 不变；尾部"钳制时间"记录内容变化 → 更新
            SyncResult third = service.run(DateUtil.toIso(since), to2, 20);
            assertTrue(third.getUpdated() > 0,
                    "窗口上界推进应产生更新记录: added=" + third.getAdded()
                            + " updated=" + third.getUpdated() + " skipped=" + third.getSkipped());
            // 已见行数 = 各次新增之和（UPDATED 是 REPLACE 同一行，不增加行数）
            assertEquals(first.getAdded() + third.getAdded(),
                    wm.seenCount("mock"), "已见行数应等于各次新增之和");
        }
    }

    @Test
    void watermarkAdvancesAfterRun() {
        String db = tmp.resolve("state2.db").toString();
        try (WatermarkManager wm = new WatermarkManager(db)) {
            SyncService service = new SyncService(new MockSource(false),
                    new StorageManager(tmp.toString()), wm);
            SyncResult r = service.run(null, DateUtil.nowUtc(), 10);
            assertTrue(wm.getWatermark("mock").isPresent(), "运行后应写入 watermark");
            // watermark 推进到窗口内最大发布时间：应严格大于起点、不晚于窗口上界
            LocalDateTime wmTime = DateUtil.parse(wm.getWatermark("mock").get());
            assertTrue(wmTime.isAfter(DateUtil.parse(r.getSince())),
                    "watermark 应晚于起点: " + wmTime);
            assertTrue(!wmTime.isAfter(DateUtil.parse(r.getUntil())),
                    "watermark 不应晚于窗口上界: " + wmTime);
        }
    }
}
