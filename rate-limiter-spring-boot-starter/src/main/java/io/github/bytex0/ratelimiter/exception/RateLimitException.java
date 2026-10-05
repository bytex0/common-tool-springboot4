package io.github.bytex0.ratelimiter.exception;

/**
 * 限流拒绝(RateLimitException)表示当前额度不足
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException() { super("请求过于频繁"); }
}
