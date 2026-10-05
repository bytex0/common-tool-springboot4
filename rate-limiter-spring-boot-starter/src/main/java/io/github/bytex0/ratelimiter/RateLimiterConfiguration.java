package io.github.bytex0.ratelimiter;

import io.github.bytex0.ratelimiter.aspect.RateLimiterAspect;
import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.core.RateLimiterFactory;
import io.github.bytex0.ratelimiter.core.RateLimiterStrategy;
import io.github.bytex0.ratelimiter.core.impl.GuavaRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.LocalRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisFixedWindowRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisLeakyBucketRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisSlidingWindowRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisTokenBucketRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedissonRateLimiterStrategyImpl;
import java.util.List;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 限流配置(RateLimiterConfiguration)默认本地可用，分布式按需连接
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterConfiguration {

    /**
     * 装配支持两种后端的限流模板，不主动创建或连接 Redis 客户端。
     *
     * @param redis Redisson 客户端提供器
     * @param strings 字符串 Redis 模板，优先选用
     * @param templates 通用 Redis 模板，未提供字符串模板时使用
     * @param environment 配置环境
     * @return 独立的限流模板
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterTemplate rateLimiterTemplate(ObjectProvider<RedissonClient> redis,
                                                   ObjectProvider<StringRedisTemplate> strings,
                                                   ObjectProvider<RedisTemplate<?, ?>> templates,
                                                   Environment environment) {
        return new RateLimiterTemplate(redis::getIfAvailable, () -> {
            StringRedisTemplate stringTemplate = strings.getIfAvailable();
            return stringTemplate == null ? templates.getIfAvailable() : stringTemplate;
        }, environment.getProperty("rate-limiter.max-local-keys", Integer.class,
                RateLimiterTemplate.DEFAULT_MAX_LOCAL_KEYS), System::nanoTime);
    }

    /**
     * 装配实例级策略工厂，让用户策略参与程序化和注解限流。
     *
     * @param template 内置算法模板
     * @param strategies 用户定义的策略 Bean
     * @return 完成注册的策略工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterFactory rateLimiterFactory(RateLimiterTemplate template, List<RateLimiterStrategy> strategies) {
        return new RateLimiterFactory(template, strategies);
    }

    /**
     * 装配可独立注入的本地窗口策略。
     *
     * @param template 共享模板
     * @return 本地策略
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalRateLimiterStrategyImpl localRateLimiterStrategy(RateLimiterTemplate template) {
        return new LocalRateLimiterStrategyImpl(template);
    }

    /**
     * 装配可独立注入的 Guava 策略。
     *
     * @param template 共享模板
     * @return Guava 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public GuavaRateLimiterStrategyImpl guavaRateLimiterStrategy(RateLimiterTemplate template) {
        return new GuavaRateLimiterStrategyImpl(template);
    }

    /**
     * 装配原生 Redisson 策略，不在此处获取网络客户端。
     *
     * @param template 共享模板
     * @return Redisson 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonRateLimiterStrategyImpl redissonRateLimiterStrategy(RateLimiterTemplate template) {
        return new RedissonRateLimiterStrategyImpl(template);
    }

    /**
     * 装配双后端固定窗口策略。
     *
     * @param template 共享模板
     * @return 固定窗口策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisFixedWindowRateLimiterStrategyImpl fixedWindowRateLimiterStrategy(RateLimiterTemplate template) {
        return new RedisFixedWindowRateLimiterStrategyImpl(template);
    }

    /**
     * 装配双后端滑动窗口策略。
     *
     * @param template 共享模板
     * @return 滑动窗口策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisSlidingWindowRateLimiterStrategyImpl slidingWindowRateLimiterStrategy(RateLimiterTemplate template) {
        return new RedisSlidingWindowRateLimiterStrategyImpl(template);
    }

    /**
     * 装配双后端令牌桶策略。
     *
     * @param template 共享模板
     * @return 令牌桶策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisTokenBucketRateLimiterStrategyImpl tokenBucketRateLimiterStrategy(RateLimiterTemplate template) {
        return new RedisTokenBucketRateLimiterStrategyImpl(template);
    }

    /**
     * 装配双后端漏桶策略。
     *
     * @param template 共享模板
     * @return 漏桶策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisLeakyBucketRateLimiterStrategyImpl leakyBucketRateLimiterStrategy(RateLimiterTemplate template) {
        return new RedisLeakyBucketRateLimiterStrategyImpl(template);
    }

    /**
     * 装配 Spring AOP 切面，遵循策略工厂的覆盖规则。
     *
     * @param factory 策略工厂
     * @param beans 可信 SpEL 的 Bean 解析器来源
     * @return 限流切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterAspect rateLimiterAspect(RateLimiterFactory factory, BeanFactory beans) {
        return new RateLimiterAspect(factory, beans);
    }
}
