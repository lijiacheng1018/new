package com.dataeng.cli.http;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.util.Config;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 执行器：超时 + 限流 + 指数退避重试 + 429 Retry-After。
 * 重试策略：
 *   - 网络异常(IOException) 与 5xx / 429 重试，最多 maxRetries 次；
 *   - 退避 = 1s 起步，指数翻倍，封顶 30s；
 *   - 429 优先尊重 Retry-After 头。
 * 重试耗尽后抛出 DataEngException(SOURCE_UNREACHABLE)。
 */
public class HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);

    private final OkHttpClient client;
    private final RateLimiter limiter;
    private final int maxRetries;
    private final String userAgent;

    public HttpExecutor() {
        this(Config.timeoutMs(), Config.maxRetries(), Config.ratePerSecond(), Config.userAgent());
    }

    public HttpExecutor(int timeoutMs, int maxRetries, int ratePerSecond, String userAgent) {
        this.maxRetries = Math.max(0, maxRetries);
        this.userAgent = userAgent;
        this.limiter = new RateLimiter(ratePerSecond);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    /** GET 请求，返回响应体字符串；重试耗尽抛 DataEngException。 */
    public String get(String url) {
        long backoffMs = 1_000L;
        for (int attempt = 0; ; attempt++) {
            limiter.acquire();
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/atom+xml, application/json, */*")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                int code = resp.code();
                if (resp.isSuccessful()) {
                    ResponseBody body = resp.body();
                    if (body == null) {
                        throw new DataEngException(ErrorCode.PARSE_ERROR, "响应体为空: " + url);
                    }
                    return body.string();
                }
                if (code == 429 || code >= 500) {
                    long retryAfterMs = retryAfterMs(resp);
                    if (attempt < maxRetries) {
                        long wait = retryAfterMs > 0 ? retryAfterMs : backoffMs;
                        log.warn("HTTP {}，第 {} 次重试（等待 {}ms）: {}", code, attempt + 1, wait, url);
                        sleep(wait);
                        backoffMs = Math.min(backoffMs * 2, 30_000L);
                        continue;
                    }
                    throw new DataEngException(ErrorCode.SOURCE_UNREACHABLE,
                            "HTTP " + code + "（已重试 " + (attempt + 1) + " 次）: " + url);
                }
                // 4xx：400 通常为查询非法；404 端点不存在
                if (code == 400) {
                    throw new DataEngException(ErrorCode.PARAM_MISSING, "数据源拒绝该查询（HTTP 400）: " + url);
                }
                throw new DataEngException(ErrorCode.SOURCE_UNREACHABLE, "HTTP " + code + ": " + url);
            } catch (DataEngException e) {
                throw e;
            } catch (IOException e) {
                if (attempt < maxRetries) {
                    log.warn("网络异常，第 {} 次重试（等待 {}ms）: {}", attempt + 1, backoffMs, e.getMessage());
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 30_000L);
                    continue;
                }
                throw new DataEngException(ErrorCode.SOURCE_UNREACHABLE,
                        "网络失败（已重试 " + (attempt + 1) + " 次）: " + e.getMessage(), e);
            }
        }
    }

    private long retryAfterMs(Response resp) {
        String v = resp.header("Retry-After");
        if (v == null || v.trim().isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(v.trim()) * 1000L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataEngException(ErrorCode.SOURCE_UNREACHABLE, "请求被中断", e);
        }
    }
}
