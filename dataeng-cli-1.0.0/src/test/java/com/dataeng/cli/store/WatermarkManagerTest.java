package com.dataeng.cli.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkManagerTest {

    @TempDir
    Path tmp;

    @Test
    void watermarkRoundTrip() {
        try (WatermarkManager wm = new WatermarkManager(tmp.resolve("state.db").toString())) {
            assertFalse(wm.getWatermark("arxiv").isPresent(), "初始不应有 watermark");
            wm.setWatermark("arxiv", "2024-01-01T00:00:00");
            Optional<String> v = wm.getWatermark("arxiv");
            assertTrue(v.isPresent());
            assertEquals("2024-01-01T00:00:00", v.get());
        }
    }

    @Test
    void classifyNewThenSkippedThenUpdated() {
        try (WatermarkManager wm = new WatermarkManager(tmp.resolve("state2.db").toString())) {
            String id = "paper-1";
            String hash1 = "abc";
            assertEquals(WatermarkManager.Decision.NEW, wm.classify("arxiv", id, hash1));

            wm.upsertSeen("arxiv", id, hash1);
            assertEquals(WatermarkManager.Decision.SKIPPED, wm.classify("arxiv", id, hash1));

            // 内容变化 → 更新
            assertEquals(WatermarkManager.Decision.UPDATED, wm.classify("arxiv", id, "xyz"));
        }
    }

    @Test
    void seenCountPerSource() {
        try (WatermarkManager wm = new WatermarkManager(tmp.resolve("state3.db").toString())) {
            wm.upsertSeen("arxiv", "a", "h1");
            wm.upsertSeen("arxiv", "b", "h2");
            wm.upsertSeen("mock", "a", "h1");
            assertEquals(2, wm.seenCount("arxiv"));
            assertEquals(1, wm.seenCount("mock"));
        }
    }
}
