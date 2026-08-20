package com.dataeng.cli.source;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.http.HttpExecutor;
import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * arXiv API 数据源（export.arxiv.org，Atom XML）。
 *
 * 选型理由（README 亦有说明）：
 *  - 免费、无需 API key、无配额认证成本；
 *  - 原生支持 submittedDate 区间查询，天然适合基于时间 watermark 的增量同步；
 *  - 支持 search_query 关键词检索与 id_list 按 ID 拉取。
 */
public class ArxivSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(ArxivSource.class);

    public static final String NAME = "arxiv";
    public static final String API_BASE = "http://export.arxiv.org/api/query";

    private final HttpExecutor http;

    public ArxivSource(HttpExecutor http) {
        this.http = http;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public SearchResponse search(String query, LocalDateTime from, LocalDateTime to, int maxResults) {
        String sq = buildSearchQuery(query, from, to);
        int remaining = Math.max(1, maxResults);
        List<PaperRecord> all = new ArrayList<>();
        String lastRaw = null;
        int start = 0;

        while (all.size() < remaining) {
            int pageSize = Math.min(100, remaining - all.size());
            String url = API_BASE + "?search_query=" + encode(sq)
                    + "&start=" + start + "&max_results=" + pageSize;
            log.info("请求 arXiv: {} (start={}, max_results={})", sq, start, pageSize);
            String raw = http.get(url);
            lastRaw = raw;
            List<PaperRecord> page = parseAtom(raw);
            if (page.isEmpty()) {
                break;
            }
            all.addAll(page);
            start += page.size();
            if (page.size() < pageSize) {
                break; // 最后一页
            }
        }
        return new SearchResponse(lastRaw, all);
    }

    /**
     * 构造 arXiv search_query。
     * 支持组合：关键词 + submittedDate:[from TO to] 时间区间。
     */
    static String buildSearchQuery(String query, LocalDateTime from, LocalDateTime to) {
        StringBuilder sb = new StringBuilder();
        if (query != null && !query.trim().isEmpty()) {
            sb.append("all:").append(query.trim());
        }
        if (from != null || to != null) {
            String fromS = from == null ? "000101010000" : DateUtil.formatArxiv(from);
            String toS = to == null ? "999912312359" : DateUtil.formatArxiv(to);
            String range = "submittedDate:[" + fromS + " TO " + toS + "]";
            if (sb.length() > 0) {
                sb.append(" AND ");
            }
            sb.append(range);
        }
        if (sb.length() == 0) {
            throw new DataEngException(ErrorCode.PARAM_MISSING,
                    "arXiv 查询至少需要 query 或时间范围（sync 场景会自动使用 watermark 时间范围）");
        }
        return sb.toString();
    }

    static String encode(String s) {
        try {
            // arXiv API 接受空格编码为 + 的查询串
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("%20", "+");
        } catch (Exception e) {
            throw new DataEngException(ErrorCode.PARAM_MISSING, "查询参数编码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 arXiv Atom XML → PaperRecord 列表。
     * 兼容 <entry> 中 author/name、category term、arxiv:doi 等结构。
     */
    public static List<PaperRecord> parseAtom(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            throw new DataEngException(ErrorCode.PARSE_ERROR, "arXiv 返回空响应");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList entries = doc.getElementsByTagName("entry");
            List<PaperRecord> records = new ArrayList<>();
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                PaperRecord r = new PaperRecord();
                r.setId(extractId(entry));
                r.setTitle(text(entry, "title"));
                r.setSummary(text(entry, "summary"));
                r.setPublished(normalizeDate(text(entry, "published")));
                r.setUpdated(normalizeDate(text(entry, "updated")));
                r.setAuthors(extractAuthors(entry));
                r.setCategories(extractCategories(entry));
                r.setPrimaryCategory(extractPrimaryCategory(entry));
                r.setLink(extractLink(entry));
                r.setDoi(textNs(entry, "doi"));
                if (r.getId() == null) {
                    throw new DataEngException(ErrorCode.PARSE_ERROR,
                            "arXiv entry 缺少 id（响应格式异常）");
                }
                records.add(r);
            }
            return records;
        } catch (DataEngException e) {
            throw e;
        } catch (Exception e) {
            throw new DataEngException(ErrorCode.PARSE_ERROR, "arXiv Atom XML 解析失败: " + e.getMessage(), e);
        }
    }

    private static String extractId(Element entry) {
        String id = text(entry, "id");
        if (id == null) {
            return null;
        }
        // http://arxiv.org/abs/2401.00001v1 -> 2401.00001v1
        int slash = id.lastIndexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id.trim();
    }

    private static String extractLink(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String rel = link.getAttribute("rel");
            if (rel == null || rel.isEmpty() || "alternate".equals(rel)) {
                return link.getAttribute("href");
            }
        }
        return null;
    }

    private static List<String> extractAuthors(Element entry) {
        List<String> authors = new ArrayList<>();
        NodeList authorNodes = entry.getElementsByTagName("author");
        for (int i = 0; i < authorNodes.getLength(); i++) {
            Element author = (Element) authorNodes.item(i);
            String name = text(author, "name");
            if (name != null && !name.trim().isEmpty()) {
                authors.add(name.trim());
            }
        }
        return authors;
    }

    private static List<String> extractCategories(Element entry) {
        List<String> cats = new ArrayList<>();
        NodeList catNodes = entry.getElementsByTagName("category");
        for (int i = 0; i < catNodes.getLength(); i++) {
            Element cat = (Element) catNodes.item(i);
            String term = cat.getAttribute("term");
            if (term != null && !term.isEmpty()) {
                cats.add(term);
            }
        }
        return cats;
    }

    private static String extractPrimaryCategory(Element entry) {
        NodeList catNodes = entry.getElementsByTagName("category");
        for (int i = 0; i < catNodes.getLength(); i++) {
            Element cat = (Element) catNodes.item(i);
            if ("true".equalsIgnoreCase(cat.getAttribute("primary"))) {
                return cat.getAttribute("term");
            }
        }
        return catsEmpty(entry) ? null : firstCat(entry);
    }

    private static boolean catsEmpty(Element entry) {
        return entry.getElementsByTagName("category").getLength() == 0;
    }

    private static String firstCat(Element entry) {
        NodeList catNodes = entry.getElementsByTagName("category");
        if (catNodes.getLength() == 0) {
            return null;
        }
        return ((Element) catNodes.item(0)).getAttribute("term");
    }

    /** 带前缀的命名空间标签，如 arxiv:doi */
    private static String textNs(Element entry, String localName) {
        NodeList list = entry.getElementsByTagName("arxiv:" + localName);
        if (list.getLength() == 0) {
            list = entry.getElementsByTagName(localName);
        }
        if (list.getLength() == 0) {
            return null;
        }
        return list.item(0).getTextContent() == null ? null : list.item(0).getTextContent().trim();
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        Node n = list.item(0);
        return n.getTextContent() == null ? null : n.getTextContent().trim();
    }

    /** arXiv 返回的日期形如 2024-01-15T08:30:00Z，统一保留原样即可（validate 可解析） */
    private static String normalizeDate(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
