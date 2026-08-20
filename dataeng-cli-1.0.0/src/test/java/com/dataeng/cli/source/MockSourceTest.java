package com.dataeng.cli.source;

import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.util.DateUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSourceTest {

    @Test
    void searchIsDeterministic() {
        MockSource s = new MockSource(false);
        LocalDateTime from = DateUtil.parse("2024-01-01");
        LocalDateTime to = DateUtil.parse("2024-01-31");

        List<PaperRecord> a = s.search("flink", from, to, 10).getRecords();
        List<PaperRecord> b = s.search("flink", from, to, 10).getRecords();

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).getId(), b.get(i).getId());
            assertEquals(a.get(i).getTitle(), b.get(i).getTitle());
        }
    }

    @Test
    void searchRespectsMaxResults() {
        MockSource s = new MockSource(false);
        List<PaperRecord> r = s.search("x", null, null, 3).getRecords();
        assertFalse(r.isEmpty());
        assertTrue(r.size() <= 3);
    }

    @Test
    void datesRespectWindow() {
        MockSource s = new MockSource(false);
        LocalDateTime from = DateUtil.parse("2024-02-01");
        LocalDateTime to = DateUtil.parse("2024-02-10");
        List<PaperRecord> r = s.search("y", from, to, 20).getRecords();
        for (PaperRecord p : r) {
            LocalDateTime t = p.getPublishedTime();
            assertNotNull(t, "published 应可解析: " + p.getPublished());
            assertTrue(!t.isBefore(from) && !t.isAfter(to), "日期应在窗口内: " + t);
        }
    }

    @Test
    void idsAreUniqueInCleanMode() {
        MockSource s = new MockSource(false);
        List<PaperRecord> r = s.search("z", null, null, 20).getRecords();
        Set<String> ids = new HashSet<>();
        for (PaperRecord p : r) {
            assertTrue(ids.add(p.getId()), "ID 不应重复: " + p.getId());
        }
    }

    @Test
    void dirtyModeInjectsIssues() {
        MockSource s = new MockSource(true);
        List<PaperRecord> r = s.search("dirty", null, null, 20).getRecords();
        boolean missingTitle = false;
        boolean badDate = false;
        boolean dupId = false;
        Set<String> ids = new HashSet<>();
        for (PaperRecord p : r) {
            if (p.getTitle() == null) missingTitle = true;
            if (p.getPublished() != null && !DateUtil.isParseable(p.getPublished())) badDate = true;
            if (!ids.add(p.getId())) dupId = true;
        }
        assertTrue(missingTitle, "dirty 模式应注入缺标题记录");
        assertTrue(badDate, "dirty 模式应注入非法日期");
        assertTrue(dupId, "dirty 模式应注入重复 ID");
    }

    @Test
    void noResultQueryReturnsEmpty() {
        MockSource s = new MockSource(false);
        List<PaperRecord> r = s.search("zzz_no_such_zzz", null, null, 10).getRecords();
        assertTrue(r.isEmpty(), "含 no_such 的查询应返回空结果");
        List<PaperRecord> r2 = s.search("noresult", null, null, 10).getRecords();
        assertTrue(r2.isEmpty(), "含 noresult 的查询应返回空结果");
    }
}
