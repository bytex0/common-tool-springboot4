package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.manager.LuaScriptManager;

/**
 * 双后端 Redis 漏桶策略，以固定速率扣减积压额度而不阻塞等待。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public class RedisLeakyBucketRateLimiterStrategyImpl extends AbstractRedisRateLimiterStrategy {

    /**
     * 保留由 Spring 管理的无参构造方式。
     */
    public RedisLeakyBucketRateLimiterStrategyImpl() {
        super();
    }

    /**
     * 注入脚本执行模板。
     *
     * @param template 限流模板
     */
    public RedisLeakyBucketRateLimiterStrategyImpl(RateLimiterTemplate template) {
        super(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RateLimiterType getType() {
        return RateLimiterType.REDIS_LUA_LEAKY_BUCKET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScript() {
        return LuaScriptManager.getLeakyBucketScript();
    }
}
