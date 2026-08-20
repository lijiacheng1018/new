package com.dataeng.cli.cli;

import com.dataeng.cli.util.DateUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * demo: 一键演示子命令。
 * 用 mock 源跑通"采集 -> 增量同步(幂等) -> 质量校验"全链路，
 * 全程离线、确定性、可复现，Windows / macOS / Linux 行为完全一致，
 * 不依赖任何 shell 脚本或平台差异。
 *
 * 示例:
 *   dataeng-cli demo
 *   dataeng-cli demo --schedule        （追加定时调度演示）
 */
@Command(
        name = "demo",
        mixinStandardHelpOptions = true,
        description = "一键演示：mock 源跑通 fetch -> sync(幂等) -> validate 全链路（离线可复现）"
)
public class DemoCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "demo-data", description = "演示数据目录（默认 demo-data）")
    String output;

    @Option(names = "--schedule", description = "追加 schedule 定时调度演示（默认不跑，保持演示最快）")
    boolean schedule;

    @Override
    public Integer call() throws Exception {
        Path out = Paths.get(output);

        // 清空旧演示数据，保证每次演示从干净状态开始、结果可复现
        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                                // 忽略清理失败（只读文件等）
                            }
                        });
            }
        }

        // 显式同步起点（10 天前）：窗口足够宽、记录数足够少（3 条），
        // 使所有记录都落在窗口内部（发布时间稳定），从而"同窗口重跑 -> 全部跳过"，
        // 干净地演示幂等去重（不依赖窗口边缘时间戳）。
        String since = DateUtil.nowUtc().minusDays(10)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        banner("1/4 fetch —— 拉取原始数据（mock 源）");
        FetchCommand fetch = new FetchCommand();
        fetch.source = "mock";
        fetch.query = "Flink interval join";
        fetch.output = output;
        fetch.maxResults = 5;
        fetch.mock = true;
        fetch.call();

        banner("2/4 sync #1 —— 首次增量同步（--since " + since + "）");
        SyncCommand sync1 = new SyncCommand();
        sync1.source = "mock";
        sync1.output = output;
        sync1.maxResults = 3;
        sync1.mock = true;
        sync1.since = since;
        sync1.call();

        banner("3/4 sync #2 —— 同窗口幂等重跑（应全部跳过）");
        SyncCommand sync2 = new SyncCommand();
        sync2.source = "mock";
        sync2.output = output;
        sync2.maxResults = 3;
        sync2.mock = true;
        sync2.since = since;
        sync2.call();

        if (schedule) {
            banner("3.5/4 schedule —— 定时调度演示（3 次，间隔 2s）");
            ScheduleCommand sched = new ScheduleCommand();
            sched.source = "mock";
            sched.output = output;
            sched.maxResults = 6;
            sched.mock = true;
            sched.interval = 2;
            sched.maxRuns = 3;
            sched.call();
        }

        banner("4/4 validate —— 数据质量校验（text + JSON 报告落盘）");
        ValidateCommand v1 = new ValidateCommand();
        v1.dataDir = output + "/processed";
        v1.format = "text";
        v1.call();

        ValidateCommand v2 = new ValidateCommand();
        v2.dataDir = output + "/processed";
        v2.format = "json";
        v2.output = output + "/quality-report.json";
        v2.call();

        banner("产出目录");
        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                walk.filter(Files::isRegularFile).sorted()
                        .forEach(p -> System.out.println("  " + out.relativize(p)));
            }
        }
        System.out.println();
        System.out.println("[demo] 完成 \u2705 全链路演示通过，数据目录: " + out.toAbsolutePath());
        return 0;
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("==========================================================");
        System.out.println("  " + title);
        System.out.println("==========================================================");
    }
}
