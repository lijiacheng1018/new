package com.dataeng.cli.cli;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.model.SyncResult;
import com.dataeng.cli.service.SyncService;
import com.dataeng.cli.source.DataSource;
import com.dataeng.cli.store.StorageManager;
import com.dataeng.cli.store.WatermarkManager;
import com.dataeng.cli.util.DateUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * sync: 基于本地 watermark 增量拉取，按唯一 ID 去重，幂等。
 *
 * 示例:
 *   dataeng-cli sync --source mock --output data --mock
 *   dataeng-cli sync --source arxiv --since 2024-01-01 --output data
 */
@Command(
        name = "sync",
        mixinStandardHelpOptions = true,
        description = "基于本地 watermark 增量拉取，按唯一 ID 去重（幂等）"
)
public class SyncCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "数据源: arxiv | mock")
    String source;

    @Option(names = "--since", description = "显式指定 watermark 起点（可选，默认读 SQLite 或近 30 天）")
    String since;

    @Option(names = "--output", defaultValue = "data", description = "输出目录（默认 data）")
    String output;

    @Option(names = "--max-results", defaultValue = "100", description = "单次增量窗口最大拉取条数（默认 100）")
    int maxResults;

    @Option(names = "--state-db", description = "SQLite 状态库路径（默认 <output>/.dataeng/state.db）")
    String stateDb;

    @Option(names = "--mock", description = "强制使用本地模拟数据源（离线可演示）")
    boolean mock;

    @Override
    public Integer call() {
        if (maxResults <= 0) {
            throw new DataEngException(ErrorCode.PARAM_MISSING, "--max-results 必须大于 0");
        }
        if (since != null && !since.trim().isEmpty()) {
            try {
                DateUtil.parse(since);
            } catch (IllegalArgumentException e) {
                throw new DataEngException(ErrorCode.PARAM_MISSING, "--since 无法解析: '" + since + "'");
            }
        }

        String db = stateDb != null ? stateDb : output + "/.dataeng/state.db";
        DataSource src = CliApp.resolveSource(source, mock);

        try (WatermarkManager wm = new WatermarkManager(db)) {
            SyncService service = new SyncService(src, new StorageManager(output), wm);
            SyncResult r = service.run(since, maxResults);
            printResult(r, db);
        }
        return 0;
    }

    private void printResult(SyncResult r, String db) {
        System.out.println("== sync " + r.getSource() + " ==");
        System.out.printf("  增量窗口  : %s -> %s%n", r.getSince(), r.getUntil());
        System.out.printf("  拉取条数  : %d%n", r.getFetched());
        System.out.printf("  新增      : %d%n", r.getAdded());
        System.out.printf("  更新      : %d%n", r.getUpdated());
        System.out.printf("  跳过(去重): %d%n", r.getSkipped());
        System.out.printf("  已见总数  : %d%n", r.getTotalSeen());
        System.out.printf("  耗时      : %d ms%n", r.getTookMs());
        System.out.println("  状态库    : " + db);
        if (r.getAdded() == 0 && r.getUpdated() == 0 && r.getFetched() > 0) {
            System.out.println("  -> 幂等验证通过：窗口内记录全部命中去重");
        }
    }
}
