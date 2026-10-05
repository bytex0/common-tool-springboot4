package io.github.bytex0.ratelimiter;

import io.github.bytex0.ratelimiter.aspect.RateLimiterAspect;
import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 限流配置(RateLimiterConfiguration)默认本地可用，分布式按需连接
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterTemplate rateLimiterTemplate(ObjectProvider<RedissonClient> redis, Environment environment) {
        return new RateLimiterTemplate(redis::getIfAvailable, environment.getProperty("rate-limiter.max-local-keys", Integer.class, 10000));
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterAspect rateLimiterAspect(RateLimiterTemplate template, BeanFactory beans) {
        return new RateLimiterAspect(template, beans);
    }
}
