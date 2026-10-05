package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;

/**
 * 本地固定窗口策略，补齐原枚举存在但缺少实现的 LOCAL 类型。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public class LocalRateLimiterStrategyImpl extends AbstractTemplateRateLimiterStrategy {

    /**
     * 注入实例隔离的本地状态模板。
     *
     * @param template 限流模板
     */
    public LocalRateLimiterStrategyImpl(RateLimiterTemplate template) {
        super(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RateLimiterType getType() {
        return RateLimiterType.LOCAL;
    }
}
