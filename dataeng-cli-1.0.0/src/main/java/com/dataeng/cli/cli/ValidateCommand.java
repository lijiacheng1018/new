package com.dataeng.cli.cli;

import com.dataeng.cli.model.QualityReport;
import com.dataeng.cli.util.JsonUtil;
import com.dataeng.cli.validate.DataValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * validate: 数据质量校验，输出含 pass/comment 的报告。
 *
 * 示例:
 *   dataeng-cli validate data/processed --format json
 *   dataeng-cli validate data/processed --output result.json
 * 退出码: 0 通过；8 校验未通过（存在 ERROR 级问题）。
 */
@Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "数据质量校验：必填字段完整率 / 重复率 / schema 类型格式 / 过期记录"
)
public class ValidateCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "data_dir", description = "包含采集结果 JSON 的数据目录")
    String dataDir;

    @Option(names = "--format", defaultValue = "text", description = "输出格式: text | json（默认 text）")
    String format;

    @Option(names = "--output", description = "将 JSON 报告写入文件（加分项，如 result.json）")
    String output;

    @Override
    public Integer call() {
        Path dir = Paths.get(dataDir);
        QualityReport report = new DataValidator().validate(dir);

        boolean wantJson = "json".equalsIgnoreCase(format) || output != null;
        if (wantJson) {
            String json = JsonUtil.toPrettyJson(report);
            if (output != null) {
                JsonUtil.writeToFile(Paths.get(output), report);
                System.out.println("校验报告已写入: " + Paths.get(output).toAbsolutePath());
            } else {
                System.out.println(json);
            }
        }
        printText(report);

        return report.isPass() ? 0 : 8;
    }

    private void printText(QualityReport r) {
        System.out.println("== validate 数据质量报告 ==");
        System.out.println("  结论    : " + (r.isPass() ? "通过 (PASS)" : "未通过 (FAIL)"));
        System.out.println("  comment : " + r.getComment());
        System.out.println("  记录数  : " + r.getTotalRecords());
        System.out.println("  重复率  : " + String.format("%.1f%%", r.getDuplicateRate() * 100)
                + "（重复 " + r.getDuplicateCount() + "）");
        if (!r.getFieldCompleteness().isEmpty()) {
            System.out.println("  必填字段完整率:");
            for (String f : r.getFieldCompleteness().keySet()) {
                System.out.printf("    %-10s : %.1f%%%n", f, r.getFieldCompleteness().get(f) * 100);
            }
        }
        if (r.getIssues().isEmpty()) {
            System.out.println("  问题明细: 无");
        } else {
            System.out.println("  问题明细:");
            for (QualityReport.Issue i : r.getIssues()) {
                System.out.printf("    [%s][%s] %s: %s%n", i.getLevel(), i.getCheck(), i.getField(), i.getMessage());
            }
        }
    }
}
