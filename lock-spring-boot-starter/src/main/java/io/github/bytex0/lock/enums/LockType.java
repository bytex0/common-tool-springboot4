package io.github.bytex0.lock.enums;

/**
 * 锁类型(LockType)本地及Redis作用域
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
public enum LockType {

    /**
     * 当前模板实例内的可重入互斥锁，不提供跨实例互斥。
     */
    REENTRANT_LOCK,

    /**
     * 当前模板实例内的计数信号量，每次调用获取一个凭证。
     */
    SEMAPHORE,

    /**
     * Redisson 分布式可重入互斥锁，默认 watchdog 续租。
     */
    REDISSON_LOCK,

    /**
     * Redisson 分布式公平锁，遵循其排队语义。
     */
    REDISSON_FAIR_LOCK,

    /**
     * Redisson 分布式自旋锁，按 SDK 退避策略竞争。
     */
    REDISSON_SPIN_LOCK,

    /**
     * 分布式读锁，可与相同键的其他读锁并行，排斥写锁。
     */
    REDISSON_READ_LOCK,

    /**
     * 分布式写锁，排斥相同键的读锁和写锁。
     */
    REDISSON_WRITE_LOCK,

    /**
     * 原项目声明但未实现的读写类型，兼容为写锁；读模式使用 REDISSON_READ_LOCK。
     */
    REDISSON_READ_WRITE_LOCK,

    /**
     * 分布式信号量，使用有归属和期限的凭证；可显式选择 RedisTemplate 后端。
     */
    REDISSON_SEMAPHORE,

    /**
     * RedisTemplate Lua 信号量，不以 Redisson 冒充该实现。
     */
    REDIS_TEMPLATE_SEMAPHORE
}
