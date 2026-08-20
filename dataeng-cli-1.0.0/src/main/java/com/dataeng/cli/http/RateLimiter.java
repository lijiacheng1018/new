package com.dataeng.cli.http;

/**
 * 简单限流器：固定速率令牌（最小请求间隔），线程安全。
 */
public final class RateLimiter {

    private final long minIntervalNanos;
    private long nextAllowedNanos = 0;

    public RateLimiter(int permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond 必须 > 0");
        }
        this.minIntervalNanos = 1_000_000_000L / permitsPerSecond;
    }

    /** 阻塞直到允许发起下一次请求 */
    public synchronized void acquire() {
        long now = System.nanoTime();
        long wait = nextAllowedNanos - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        nextAllowedNanos = Math.max(now, nextAllowedNanos) + minIntervalNanos;
    }
}
