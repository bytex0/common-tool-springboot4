package io.github.bytex0.ratelimiter.enums;

/**
 * 限流类型(RateLimiterType)本地及Redis策略
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
public enum RateLimiterType {
    LOCAL, GUAVA, REDISSON, REDIS_LUA_FIXED_WINDOW, REDIS_LUA_SLIDING_WINDOW,
    REDIS_LUA_TOKEN_BUCKET, REDIS_LUA_LEAKY_BUCKET
}
