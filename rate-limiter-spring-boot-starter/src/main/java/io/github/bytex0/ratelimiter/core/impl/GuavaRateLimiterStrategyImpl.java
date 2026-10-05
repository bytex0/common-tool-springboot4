package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;

/**
 * Guava 预热策略，使用有界且按规则区分的本地状态。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public class GuavaRateLimiterStrategyImpl extends AbstractTemplateRateLimiterStrategy {

    /**
     * 保留原无参构造入口，创建无外部依赖且有界的实例级 Guava 状态。
     */
    public GuavaRateLimiterStrategyImpl() {
        this(new RateLimiterTemplate(() -> null, RateLimiterTemplate.DEFAULT_MAX_LOCAL_KEYS));
    }

    /**
     * 注入当前上下文的状态模板。
     *
     * @param template 限流模板
     */
    public GuavaRateLimiterStrategyImpl(RateLimiterTemplate template) {
        super(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RateLimiterType getType() {
        return RateLimiterType.GUAVA;
    }
}
