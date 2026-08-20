package com.dataeng.cli.source;

import com.dataeng.cli.model.PaperRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArxivParserTest {

    private static final String SAMPLE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:arxiv=\"http://arxiv.org/schemas/atom\">"
            + "  <title>ArXiv Query</title>"
            + "  <entry>"
            + "    <id>http://arxiv.org/abs/2401.00001v1</id>"
            + "    <updated>2024-01-01T12:00:00Z</updated>"
            + "    <published>2024-01-01T08:30:00Z</published>"
            + "    <title>Towards Efficient Interval Joins on Data Streams</title>"
            + "    <summary>We study interval joins over streaming data.</summary>"
            + "    <author><name>Ada Lovelace</name></author>"
            + "    <author><name>Alan Turing</name></author>"
            + "    <arxiv:doi>10.48550/arXiv.2401.00001</arxiv:doi>"
            + "    <link href=\"http://arxiv.org/abs/2401.00001v1\" rel=\"alternate\" type=\"text/html\"/>"
            + "    <category term=\"cs.DC\" primary=\"true\"/>"
            + "    <category term=\"cs.DS\"/>"
            + "  </entry>"
            + "  <entry>"
            + "    <id>http://arxiv.org/abs/2401.00002v2</id>"
            + "    <updated>2024-01-02T00:00:00Z</updated>"
            + "    <published>2024-01-01T09:00:00Z</published>"
            + "    <title>Second Paper Title</title>"
            + "    <summary>Second summary.</summary>"
            + "    <author><name>Grace Hopper</name></author>"
            + "  </entry>"
            + "</feed>";

    @Test
    void parsesEntryFields() {
        List<PaperRecord> records = ArxivSource.parseAtom(SAMPLE);
        assertEquals(2, records.size());

        PaperRecord r = records.get(0);
        assertEquals("2401.00001v1", r.getId());
        assertEquals("Towards Efficient Interval Joins on Data Streams", r.getTitle());
        assertEquals(2, r.getAuthors().size());
        assertEquals("Ada Lovelace", r.getAuthors().get(0));
        assertEquals("2024-01-01T08:30:00Z", r.getPublished());
        assertEquals("10.48550/arXiv.2401.00001", r.getDoi());
        assertEquals("cs.DC", r.getPrimaryCategory());
        assertTrue(r.getCategories().contains("cs.DS"));
        assertEquals("http://arxiv.org/abs/2401.00001v1", r.getLink());
        assertTrue(r.getSummary().contains("interval joins"));
    }

    @Test
    void buildSearchQueryCombinesQueryAndRange() {
        String q = ArxivSource.buildSearchQuery("flink", null, null);
        assertEquals("all:flink", q);

        String q2 = ArxivSource.buildSearchQuery(null,
                java.time.LocalDateTime.of(2024, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2024, 1, 15, 0, 0));
        assertEquals("submittedDate:[202401010000 TO 202401150000]", q2);

        String q3 = ArxivSource.buildSearchQuery("flink",
                java.time.LocalDateTime.of(2024, 1, 1, 0, 0), null);
        assertEquals("all:flink AND submittedDate:[202401010000 TO 999912312359]", q3);
    }
}
