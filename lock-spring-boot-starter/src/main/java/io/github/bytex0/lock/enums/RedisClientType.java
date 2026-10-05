package io.github.bytex0.lock.enums;

/**
 * 锁客户端(RedisClientType)选择分布式信号量使用的实际后端。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:48:45
 */
public enum RedisClientType {

    /**
     * 使用 Redisson 的锁或带过期凭证的信号量。
     */
    REDISSON,

    /**
     * 使用 RedisTemplate 执行凭证化 Lua 信号量，不支持 Redisson 专用互斥锁。
     */
    REDIS_TEMPLATE
}
