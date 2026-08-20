package com.dataeng.cli.cli;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.source.DataSource;
import com.dataeng.cli.source.SourceFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * CLI 主入口：dataeng-cli [fetch|sync|validate|schedule] ...
 * 统一异常处理：DataEngException -> "错误[CODE]: 消息" + 对应退出码。
 */
@Command(
        name = "dataeng-cli",
        mixinStandardHelpOptions = true,
        version = "dataeng-cli 1.0.0",
        description = "科研公共数据源采集与集成 CLI 工具（fetch / sync / validate / schedule / demo）",
        subcommands = {FetchCommand.class, SyncCommand.class, ValidateCommand.class, ScheduleCommand.class, DemoCommand.class}
)
public class CliApp implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new CliApp());
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            if (ex instanceof DataEngException) {
                DataEngException de = (DataEngException) ex;
                System.err.println(de.toCliString());
                return de.exitCode();
            }
            System.err.println("未预期错误: " + ex);
            ex.printStackTrace(System.err);
            return 1;
        });
        int code = cmd.execute(args);
        System.exit(code);
    }

    /** 命令通用辅助：解析数据源 */
    static DataSource resolveSource(String name, boolean forceMock) {
        return SourceFactory.create(name, forceMock);
    }
}
