package io.github.bytex0.idempotent;

import io.github.bytex0.idempotent.aspect.IdempotentAspect;
import io.github.bytex0.idempotent.config.IdempotentProperties;
import io.github.bytex0.idempotent.core.IdempotentKeyGenerator;
import io.github.bytex0.idempotent.core.RedisIdempotentExecutor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 幂等配置(IdempotentConfiguration)按需执行且支持用户替换
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@AutoConfiguration
@EnableConfigurationProperties(IdempotentProperties.class)
@ConditionalOnProperty(prefix = "idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotentConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentKeyGenerator idempotentKeyGenerator(BeanFactory beans) { return new IdempotentKeyGenerator(beans); }

    @Bean
    @ConditionalOnMissingBean
    public RedisIdempotentExecutor redisIdempotentExecutor(ObjectProvider<RedissonClient> redis) {
        return new RedisIdempotentExecutor(redis::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(RedisIdempotentExecutor executor, IdempotentKeyGenerator keys, IdempotentProperties properties) {
        return new IdempotentAspect(executor, keys, properties);
    }
}
