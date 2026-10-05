package io.github.bytex0.ratelimiter.enums;

/**
 * Redis Lua 限流脚本的执行后端，不改变算法、键或额度语义。
 *
 * @author bytex0
 * @since 2026-10-05 20:36:23
 */
public enum RedisClientType {

    /**
     * 通过 RedissonClient 执行脚本，键与参数使用 UTF-8 字符串编码。
     */
    REDISSON,

    /**
     * 通过 RedisTemplate 的连接执行脚本，不依赖其业务值序列化器。
     */
    REDIS_TEMPLATE
}
