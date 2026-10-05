package io.github.bytex0.lock.core.impl;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;

/**
 * 本地信号量(SemaphoreStrategyImpl)按成功获取记录释放归属。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class SemaphoreStrategyImpl extends AbstractTemplateLockStrategy {

    /**
     * 创建独立策略，调用方负责关闭。
     */
    public SemaphoreStrategyImpl() {
        super(new LockTemplate(() -> null), true);
    }

    /**
     * 使用容器管理的共享模板。
     *
     * @param template 共享模板
     */
    public SemaphoreStrategyImpl(LockTemplate template) {
        super(template, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockType getType() {
        return LockType.SEMAPHORE;
    }
}
