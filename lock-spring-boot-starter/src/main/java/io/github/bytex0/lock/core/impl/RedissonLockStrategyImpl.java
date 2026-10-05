package io.github.bytex0.lock.core.impl;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;
import org.redisson.api.RedissonClient;

/**
 * Redis互斥策略(RedissonLockStrategyImpl)保留原客户端构造，支持 watchdog。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class RedissonLockStrategyImpl extends AbstractTemplateLockStrategy {

    /**
     * 保留原直接客户端构造，关闭策略不关闭外部客户端。
     *
     * @param client 外部管理的客户端
     */
    public RedissonLockStrategyImpl(RedissonClient client) {
        super(new LockTemplate(() -> client), true);
    }

    /**
     * 使用容器共享模板。
     *
     * @param template 共享模板
     */
    public RedissonLockStrategyImpl(LockTemplate template) {
        super(template, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockType getType() {
        return LockType.REDISSON_LOCK;
    }
}
