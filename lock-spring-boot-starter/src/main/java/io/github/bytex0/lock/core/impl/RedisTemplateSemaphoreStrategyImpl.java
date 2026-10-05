package io.github.bytex0.lock.core.impl;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.model.LockRule;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 模板信号量策略(RedisTemplateSemaphoreStrategyImpl)恢复真实 Lua 后端及原 release 入口。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class RedisTemplateSemaphoreStrategyImpl extends AbstractTemplateLockStrategy {

    /**
     * 保留原直接模板构造，兼容字符串或对象序列化模板，不拥有其连接。
     *
     * @param template 外部模板
     */
    public RedisTemplateSemaphoreStrategyImpl(RedisTemplate<?, ?> template) {
        super(new LockTemplate(() -> null, () -> template, LockTemplate.DEFAULT_MAX_SCOPES), true);
    }

    /**
     * 使用共享模板。
     *
     * @param template 共享锁模板
     */
    public RedisTemplateSemaphoreStrategyImpl(LockTemplate template) {
        super(template, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockType getType() {
        return LockType.REDIS_TEMPLATE_SEMAPHORE;
    }

    /**
     * 保留原 release 别名，仅释放当前线程成功取得的凭证。
     *
     * @param rule 获取时的规则
     */
    public void release(LockRule rule) {
        unlock(rule);
    }
}
