package io.github.bytex0.lock.core.impl;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;

/**
 * 本地互斥策略(ReentrantLockStrategyImpl)保留无参构造及原策略接口。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class ReentrantLockStrategyImpl extends AbstractTemplateLockStrategy {

    /**
     * 创建独立实例，调用方使用完毕后关闭策略。
     */
    public ReentrantLockStrategyImpl() {
        super(new LockTemplate(() -> null), true);
    }

    /**
     * 注入共享模板，模板由容器关闭。
     *
     * @param template 共享模板
     */
    public ReentrantLockStrategyImpl(LockTemplate template) {
        super(template, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockType getType() {
        return LockType.REENTRANT_LOCK;
    }
}
