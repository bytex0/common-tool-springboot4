package io.github.bytex0.lock.core.impl;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;
import org.redisson.api.RedissonClient;

/**
 * 自旋锁策略(RedissonSpinLockStrategyImpl)修复原实现误取公平锁的问题。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class RedissonSpinLockStrategyImpl extends AbstractTemplateLockStrategy {

    /**
     * 保留原直接客户端构造，调用方负责关闭策略和客户端。
     *
     * @param client 外部客户端
     */
    public RedissonSpinLockStrategyImpl(RedissonClient client) {
        super(new LockTemplate(() -> client), true);
    }

    /**
     * 使用共享模板。
     *
     * @param template 共享模板
     */
    public RedissonSpinLockStrategyImpl(LockTemplate template) {
        super(template, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockType getType() {
        return LockType.REDISSON_SPIN_LOCK;
    }
}
