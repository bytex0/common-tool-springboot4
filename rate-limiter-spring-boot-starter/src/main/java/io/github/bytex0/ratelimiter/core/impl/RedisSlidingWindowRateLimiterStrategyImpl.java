package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.manager.LuaScriptManager;

/**
 * 双后端 Redis 滑动窗口策略，使用服务端时钟而非应用节点时钟。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public class RedisSlidingWindowRateLimiterStrategyImpl extends AbstractRedisRateLimiterStrategy {

    /**
     * 保留由 Spring 管理的无参构造方式。
     */
    public RedisSlidingWindowRateLimiterStrategyImpl() {
        super();
    }

    /**
     * 注入脚本执行模板。
     *
     * @param template 限流模板
     */
    public RedisSlidingWindowRateLimiterStrategyImpl(RateLimiterTemplate template) {
        super(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RateLimiterType getType() {
        return RateLimiterType.REDIS_LUA_SLIDING_WINDOW;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScript() {
        return LuaScriptManager.getSlidingWindowScript();
    }
}
