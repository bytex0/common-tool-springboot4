package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import org.redisson.api.RedissonClient;

/**
 * Redisson 原生分布式限流策略，支持规则更新后的状态隔离及加权许可。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public class RedissonRateLimiterStrategyImpl extends AbstractTemplateRateLimiterStrategy {

    /**
     * 保留原直接传入 RedissonClient 的构造方式，不接管该客户端生命周期。
     *
     * @param client 由调用方管理的 Redisson 客户端
     */
    public RedissonRateLimiterStrategyImpl(RedissonClient client) {
        this(new RateLimiterTemplate(() -> client, RateLimiterTemplate.DEFAULT_MAX_LOCAL_KEYS));
    }

    /**
     * 注入按需获取 Redisson 客户端的模板。
     *
     * @param template 限流模板
     */
    public RedissonRateLimiterStrategyImpl(RateLimiterTemplate template) {
        super(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RateLimiterType getType() {
        return RateLimiterType.REDISSON;
    }
}
