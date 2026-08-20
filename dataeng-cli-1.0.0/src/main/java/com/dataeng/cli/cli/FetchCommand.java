package com.dataeng.cli.cli;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.model.FetchResult;
import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.source.DataSource;
import com.dataeng.cli.source.SearchResponse;
import com.dataeng.cli.store.StorageManager;
import com.dataeng.cli.util.DateUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * fetch: 按关键词/ID 从数据源拉取原始数据并落盘。
 *
 * 示例:
 *   dataeng-cli fetch --source arxiv --query "Flink interval join" --output data --max-results 5
 *   dataeng-cli fetch --source mock --query "streaming" --output data --mock
 */
@Command(
        name = "fetch",
        mixinStandardHelpOptions = true,
        description = "按关键词/ID 从数据源拉取原始数据并落盘"
)
public class FetchCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "数据源: arxiv | mock")
    String source;

    @Option(names = "--query", required = true, description = "查询关键词或 ID")
    String query;

    @Option(names = "--output", defaultValue = "data", description = "输出目录（默认 data）")
    String output;

    @Option(names = "--max-results", defaultValue = "10", description = "最大返回条数（默认 10）")
    int maxResults;

    @Option(names = "--since", description = "起始时间，格式 yyyy-MM-dd 或 ISO（可选）")
    String since;

    @Option(names = "--until", description = "结束时间，格式 yyyy-MM-dd 或 ISO（可选）")
    String until;

    @Option(names = "--mock", description = "强制使用本地模拟数据源（离线可演示）")
    boolean mock;

    @Override
    public Integer call() {
        if (query == null || query.trim().isEmpty()) {
            throw new DataEngException(ErrorCode.PARAM_MISSING, "--query 不能为空");
        }
        if (maxResults <= 0) {
            throw new DataEngException(ErrorCode.PARAM_MISSING, "--max-results 必须大于 0");
        }

        long t0 = System.currentTimeMillis();
        DataSource src = CliApp.resolveSource(source, mock);
        LocalDateTime from = parseOptional(since, "--since");
        LocalDateTime to = parseOptional(until, "--until");

        System.out.printf("== fetch %s | query='%s' | max=%d ==%n", src.name(), query, maxResults);
        SearchResponse resp = src.search(query, from, to, maxResults);
        List<PaperRecord> records = resp.getRecords();

        if (records.isEmpty()) {
            throw new DataEngException(ErrorCode.NO_RESULTS,
                    "查询 '" + query + "' 在 " + src.name() + " 上没有返回任何结果");
        }

        StorageManager storage = new StorageManager(output);
        Path rawFile = storage.saveRaw(src.name(), query, resp.getRawBody());
        Path processedFile = storage.saveProcessed(src.name(), records);

        FetchResult result = new FetchResult();
        result.setSource(src.name());
        result.setQuery(query);
        result.setCount(records.size());
        result.setRawFile(rawFile.toString());
        result.setProcessedFile(processedFile.toString());
        result.setTookMs(System.currentTimeMillis() - t0);

        printSummary(records);
        System.out.println();
        System.out.println("原始响应已落盘 : " + rawFile);
        System.out.println("处理后 JSON    : " + processedFile);
        System.out.println("耗时           : " + result.getTookMs() + " ms");
        return 0;
    }

    private void printSummary(List<PaperRecord> records) {
        System.out.printf("共拉取 %d 条记录:%n", records.size());
        int n = Math.min(records.size(), 5);
        for (int i = 0; i < n; i++) {
            PaperRecord r = records.get(i);
            System.out.printf("  [%d] %s | %s | %s%n",
                    i + 1,
                    r.getId(),
                    DateUtil.human(r.getPublishedTime()),
                    truncate(r.getTitle(), 60));
        }
        if (records.size() > n) {
            System.out.printf("  ... 其余 %d 条见落盘 JSON%n", records.size() - n);
        }
    }

    private static LocalDateTime parseOptional(String s, String flag) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return DateUtil.parse(s);
        } catch (IllegalArgumentException e) {
            throw new DataEngException(ErrorCode.PARAM_MISSING, flag + " 无法解析: '" + s + "'");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "(null)";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
