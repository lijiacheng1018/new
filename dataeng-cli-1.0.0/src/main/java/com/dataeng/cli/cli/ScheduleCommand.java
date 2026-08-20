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
 * schedule（加分项）：定时调度演示。
 * 周期性执行增量 sync，观察 watermark 游标推进与"新增/更新/跳过"计数变化。
 *
 * 示例:
 *   dataeng-cli schedule --source mock --output data --mock --interval 3 --max-runs 3
 */
@Command(
        name = "schedule",
        mixinStandardHelpOptions = true,
        description = "定时调度演示：周期性执行增量同步（观察 watermark 推进）"
)
public class ScheduleCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "数据源: arxiv | mock")
    String source;

    @Option(names = "--interval", defaultValue = "3", description = "每次运行间隔秒数（默认 3）")
    int interval;

    @Option(names = "--max-runs", defaultValue = "3", description = "运行次数（默认 3）")
    int maxRuns;

    @Option(names = "--output", defaultValue = "data", description = "输出目录（默认 data）")
    String output;

    @Option(names = "--max-results", defaultValue = "10", description = "单次窗口最大拉取条数（默认 10）")
    int maxResults;

    @Option(names = "--mock", description = "强制使用本地模拟数据源（离线可演示）")
    boolean mock;

    @Override
    public Integer call() {
        if (interval <= 0 || maxRuns <= 0) {
            throw new DataEngException(ErrorCode.PARAM_MISSING, "--interval / --max-runs 必须大于 0");
        }
        String db = output + "/.dataeng/state.db";
        DataSource src = CliApp.resolveSource(source, mock);

        try (WatermarkManager wm = new WatermarkManager(db)) {
            SyncService service = new SyncService(src, new StorageManager(output), wm);
            for (int i = 1; i <= maxRuns; i++) {
                System.out.printf("%n===== 第 %d/%d 次调度执行 @ %s =====%n",
                        i, maxRuns, DateUtil.human(DateUtil.nowUtc()));
                SyncResult r = service.run(null, maxResults);
                System.out.printf("  窗口 %s -> %s | fetched=%d added=%d updated=%d skipped=%d | 已见=%d%n",
                        r.getSince(), r.getUntil(), r.getFetched(),
                        r.getAdded(), r.getUpdated(), r.getSkipped(), r.getTotalSeen());
                if (i < maxRuns) {
                    System.out.printf("  休眠 %d 秒后继续...%n", interval);
                    try {
                        Thread.sleep(interval * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return 0;
    }
}
