package com.dataeng.cli.util;

/**
 * 运行配置：全部可通过环境变量覆盖，默认值适合普通网络环境。
 */
public final class Config {

    private Config() {
    }

    public static int timeoutMs() {
        return envInt("DATAENG_TIMEOUT_MS", 20000);
    }

    public static int maxRetries() {
        return envInt("DATAENG_MAX_RETRIES", 3);
    }

    /** 每秒最大请求数（限流） */
    public static int ratePerSecond() {
        return envInt("DATAENG_RATE_PER_SECOND", 5);
    }

    public static String userAgent() {
        String v = System.getenv("DATAENG_USER_AGENT");
        return v == null || v.trim().isEmpty() ? "dataeng-cli/1.0.0 (+research-data-engineering-demo)" : v.trim();
    }

    /** 日志级别：DEBUG | INFO | WARN | ERROR */
    public static String logLevel() {
        String v = System.getenv("DATAENG_LOG_LEVEL");
        return v == null || v.trim().isEmpty() ? "INFO" : v.trim().toUpperCase();
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
