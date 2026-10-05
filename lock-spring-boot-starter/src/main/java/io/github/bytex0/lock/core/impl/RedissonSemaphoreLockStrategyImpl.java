package io.github.bytex0.lock.core.impl;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;
import org.redisson.api.RedissonClient;

/**
 * 分布式信号量(RedissonSemaphoreLockStrategyImpl)以带期限凭证替换无归属计数。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class RedissonSemaphoreLockStrategyImpl extends AbstractTemplateLockStrategy {

    /**
     * 保留原客户端构造，调用方负责关闭策略，客户端不会被策略关闭。
     *
     * @param client 外部客户端
     */
    public RedissonSemaphoreLockStrategyImpl(RedissonClient client) {
        super(new LockTemplate(() -> client), true);
    }

    /**
     * 使用共享模板。
     *
     * @param template 共享模板
     */
    public RedissonSemaphoreLockStrategyImpl(LockTemplate template) {
        super(template, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockType getType() {
        return LockType.REDISSON_SEMAPHORE;
    }
}
