package io.github.bytex0.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 多Redis(MultiRedissonConfig)按需自动配置
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
@AutoConfiguration
@EnableConfigurationProperties(MultiRedisProperties.class)
@ConditionalOnProperty(prefix = "multi-redis", name = "enabled", havingValue = "true")
public class MultiRedissonConfig {

    @Bean
    @ConditionalOnMissingBean
    public RedisClientFactory redisClientFactory() { return Redisson::create; }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MultiRedisManager multiRedisManager(MultiRedisProperties properties, RedisClientFactory factory) {
        return new MultiRedisManager(properties, factory);
    }

    @Bean(destroyMethod = "")
    @Primary
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(MultiRedisManager manager) { return manager.primary(); }
}
