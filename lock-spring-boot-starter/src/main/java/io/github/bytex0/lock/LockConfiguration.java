package io.github.bytex0.lock;

import io.github.bytex0.lock.aspect.LockAspect;
import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.core.LockFactory;
import io.github.bytex0.lock.core.LockStrategy;
import io.github.bytex0.lock.core.impl.ReentrantLockStrategyImpl;
import io.github.bytex0.lock.core.impl.SemaphoreStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonFairLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonSpinLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonSemaphoreLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedisTemplateSemaphoreStrategyImpl;
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

import java.util.List;

/**
 * 锁配置(LockConfiguration)本地默认可用，Redis按需获取
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LockConfiguration {

    /**
     * 注册双后端模板，不获取或连接任何 Redis 客户端。
     *
     * @param redis Redisson 提供器
     * @param strings 字符串模板，优先使用
     * @param templates 通用模板后备
     * @param environment 容量配置来源
     * @return 锁模板
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(ObjectProvider<RedissonClient> redis, ObjectProvider<StringRedisTemplate> strings,
                                     ObjectProvider<RedisTemplate<?, ?>> templates, Environment environment) {
        return new LockTemplate(redis::getIfAvailable, () -> {
            StringRedisTemplate candidate = strings.getIfAvailable();
            return candidate == null ? templates.getIfAvailable() : candidate;
        }, environment.getProperty("lock.max-scopes", Integer.class, LockTemplate.DEFAULT_MAX_SCOPES));
    }

    /**
     * 保留此前模板工厂入口。
     *
     * @param redis Redisson 提供器
     * @return 锁模板
     */
    public LockTemplate lockTemplate(ObjectProvider<RedissonClient> redis) {
        return new LockTemplate(redis::getIfAvailable);
    }

    /**
     * 注册支持自定义覆盖的策略工厂。
     *
     * @param template 共享模板
     * @param strategies 全部策略 Bean
     * @return 工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public LockFactory lockFactory(LockTemplate template, List<LockStrategy> strategies) {
        return new LockFactory(template, strategies);
    }

    /**
     * 注册使用工厂的切面。
     *
     * @param factory 策略工厂
     * @param beans 当前容器
     * @return 切面
     */
    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(LockFactory factory, BeanFactory beans) {
        return new LockAspect(factory, beans);
    }

    /**
     * 保留此前直接模板切面入口。
     *
     * @param template 共享模板
     * @param beans 当前容器
     * @return 切面
     */
    public LockAspect lockAspect(LockTemplate template, BeanFactory beans) {
        return new LockAspect(template, beans);
    }

    /**
     * 注册本地互斥策略。
     *
     * @param template 共享模板
     * @return 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public ReentrantLockStrategyImpl reentrantLockStrategy(LockTemplate template) {
        return new ReentrantLockStrategyImpl(template);
    }

    /**
     * 注册本地信号量策略。
     *
     * @param template 共享模板
     * @return 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public SemaphoreStrategyImpl semaphoreStrategy(LockTemplate template) {
        return new SemaphoreStrategyImpl(template);
    }

    /**
     * 注册分布式互斥策略。
     *
     * @param template 共享模板
     * @return 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonLockStrategyImpl redissonLockStrategy(LockTemplate template) {
        return new RedissonLockStrategyImpl(template);
    }

    /**
     * 注册公平锁策略。
     *
     * @param template 共享模板
     * @return 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonFairLockStrategyImpl redissonFairLockStrategy(LockTemplate template) {
        return new RedissonFairLockStrategyImpl(template);
    }

    /**
     * 注册自旋锁策略。
     *
     * @param template 共享模板
     * @return 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonSpinLockStrategyImpl redissonSpinLockStrategy(LockTemplate template) {
        return new RedissonSpinLockStrategyImpl(template);
    }

    /**
     * 注册 Redisson 信号量策略。
     *
     * @param template 共享模板
     * @return 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonSemaphoreLockStrategyImpl redissonSemaphoreStrategy(LockTemplate template) {
        return new RedissonSemaphoreLockStrategyImpl(template);
    }

    /**
     * 注册真实 RedisTemplate 信号量策略。
     *
     * @param template 共享模板
     * @return 策略
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisTemplateSemaphoreStrategyImpl redisTemplateSemaphoreStrategy(LockTemplate template) {
        return new RedisTemplateSemaphoreStrategyImpl(template);
    }
}
