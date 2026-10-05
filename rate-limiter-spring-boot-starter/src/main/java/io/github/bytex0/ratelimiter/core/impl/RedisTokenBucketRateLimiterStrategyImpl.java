package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.manager.LuaScriptManager;

/**
 * 双后端 Redis 令牌桶策略，允许容量以内的突发请求。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public class RedisTokenBucketRateLimiterStrategyImpl extends AbstractRedisRateLimiterStrategy {

    /**
     * 保留由 Spring 管理的无参构造方式。
     */
    public RedisTokenBucketRateLimiterStrategyImpl() {
        super();
    }

    /**
     * 注入脚本执行模板。
     *
     * @param template 限流模板
     */
    public RedisTokenBucketRateLimiterStrategyImpl(RateLimiterTemplate template) {
        super(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RateLimiterType getType() {
        return RateLimiterType.REDIS_LUA_TOKEN_BUCKET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScript() {
        return LuaScriptManager.getTokenBucketScript();
    }
}
