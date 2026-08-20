package com.dataeng.cli.exception;

/**
 * 统一业务异常：携带错误码，由 CLI 全局异常处理器转成"错误[CODE]: 消息"并设置退出码。
 */
public class DataEngException extends RuntimeException {

    private final ErrorCode code;

    public DataEngException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DataEngException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public int exitCode() {
        return code.exitCode();
    }

    /** 终端友好的错误行，如: 错误[SOURCE_UNREACHABLE]: ... */
    public String toCliString() {
        return "错误[" + code.name() + "] (" + code.label() + "): " + getMessage();
    }
}
