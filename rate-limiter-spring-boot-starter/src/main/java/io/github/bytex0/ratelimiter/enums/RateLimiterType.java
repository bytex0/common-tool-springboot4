package io.github.bytex0.ratelimiter.enums;

/**
 * 限流类型(RateLimiterType)本地及Redis策略
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
public enum RateLimiterType {

    /**
     * 当前应用实例内的固定窗口计数器，不在多个应用实例间共享额度。
     */
    LOCAL,

    /**
     * Guava 预热限流器，按每秒速率补充许可，状态仅属于当前应用实例。
     */
    GUAVA,

    /**
     * Redisson 原生分布式限流器，必须提供 RedissonClient，不受 Lua 后端选项影响。
     */
    REDISSON,

    /**
     * Redis Lua 固定窗口计数，窗口内累计许可不能超过上限。
     */
    REDIS_LUA_FIXED_WINDOW,

    /**
     * Redis Lua 滑动窗口，统计最近一个窗口内尚未过期的许可。
     */
    REDIS_LUA_SLIDING_WINDOW,

    /**
     * Redis Lua 令牌桶，按每秒速率补充令牌，允许容量范围内的突发请求。
     */
    REDIS_LUA_TOKEN_BUCKET,

    /**
     * Redis Lua 漏桶，按每秒速率扣减积压量，桶满时拒绝请求而不排队。
     */
    REDIS_LUA_LEAKY_BUCKET
}
