package io.github.bytex0.lock.enums;

/**
 * 锁类型(LockType)本地及Redis作用域
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
public enum LockType {
    REENTRANT_LOCK, SEMAPHORE, REDISSON_LOCK, REDISSON_FAIR_LOCK, REDISSON_SPIN_LOCK,
    REDISSON_READ_LOCK, REDISSON_WRITE_LOCK, REDISSON_READ_WRITE_LOCK,
    REDISSON_SEMAPHORE, REDIS_TEMPLATE_SEMAPHORE
}
