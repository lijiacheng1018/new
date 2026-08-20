package com.dataeng.cli.exception;

/**
 * 错误码：CLI 全部可预期错误统一走这里，保证"清晰报错"。
 * 每个错误码附带稳定的进程退出码，便于脚本/CI 判断。
 */
public enum ErrorCode {

    /** 参数缺失或不合法 */
    PARAM_MISSING(2, "参数缺失或不合法"),

    /** 不支持的数据源 */
    UNSUPPORTED_SOURCE(2, "不支持的数据源"),

    /** 数据源不可达 / HTTP 错误 / 网络失败（重试后仍失败） */
    SOURCE_UNREACHABLE(3, "数据源不可达或请求失败"),

    /** 查询无结果 */
    NO_RESULTS(4, "查询无结果"),

    /** 响应解析失败 / 数据格式异常 */
    PARSE_ERROR(5, "响应格式异常"),

    /** 本地文件读写失败 */
    IO_ERROR(6, "本地文件读写失败"),

    /** watermark / 去重存储失败 */
    STORE_ERROR(7, "本地状态存储失败"),

    /** 校验流程执行失败 */
    VALIDATE_ERROR(8, "校验执行失败");

    private final int exitCode;
    private final String label;

    ErrorCode(int exitCode, String label) {
        this.exitCode = exitCode;
        this.label = label;
    }

    public int exitCode() {
        return exitCode;
    }

    public String label() {
        return label;
    }
}
