package io.github.bytex0.ratelimiter.example;

import io.github.bytex0.ratelimiter.aspect.RateLimiter;
import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.exception.RateLimitException;
import io.github.bytex0.ratelimiter.model.FlowRule;
import org.springframework.stereotype.Service;

/**
 * 限流业务(RateService)程序化和注解调用
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@Service
public class RateService {

    /**
     * 真实限流模板
     */
    private final RateLimiterTemplate template;

    public RateService(RateLimiterTemplate template) { this.template = template; }

    public void acquire(FlowRule rule) {
        if (!template.tryAccess(rule)) { throw new RateLimitException(); }
    }

    @RateLimiter(type = RateLimiterType.REDIS_LUA_FIXED_WINDOW, key = "#key", maxRequests = 2, windowTime = 2)
    public void annotated(String key) {
    }
}
