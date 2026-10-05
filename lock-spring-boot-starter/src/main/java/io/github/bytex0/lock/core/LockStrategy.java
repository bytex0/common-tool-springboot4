package io.github.bytex0.lock.core;

import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.model.LockRule;

/**
 * 锁策略(LockStrategy)保留原可替换策略契约，只有成功获取者才能释放。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public interface LockStrategy {

    /**
     * 返回该策略负责的锁类型。
     *
     * @return 锁类型，不可为空
     */
    LockType getType();

    /**
     * 可中断地阻塞获取锁，获取失败必须抛出异常，不能直接放行业务。
     *
     * @param rule 锁规则，调用方不得并发修改
     */
    void lock(LockRule rule);

    /**
     * 按规则 timeout 和 timeUnit 限时获取锁。
     *
     * @param rule 锁规则
     * @return 是否成功获取
     */
    boolean tryLock(LockRule rule);

    /**
     * 同线程释放最近一次成功获取的锁，未获取者不得释放其他线程的资源。
     *
     * @param rule 成功获取时的规则，键与类型不得改变
     */
    void unlock(LockRule rule);
}
