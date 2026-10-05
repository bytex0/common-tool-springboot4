package io.github.bytex0.lock;

import io.github.bytex0.lock.aspect.LockAspect;
import io.github.bytex0.lock.core.LockTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 锁配置(LockConfiguration)本地默认可用，Redis按需获取
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LockConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(ObjectProvider<RedissonClient> redis) { return new LockTemplate(redis::getIfAvailable); }

    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(LockTemplate template, BeanFactory beanFactory) { return new LockAspect(template, beanFactory); }
}
