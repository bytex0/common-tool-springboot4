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
import org.springframework.context.ApplicationContext;

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

    /**
     * 注册可由业务替换的键生成器。
     *
     * @param beans 当前容器
     * @return 稳定键生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentKeyGenerator idempotentKeyGenerator(BeanFactory beans) {
        return new IdempotentKeyGenerator(beans);
    }

    /**
     * 保留原无参配置调用入口，原四参数键协议由调用方提供解析器。
     *
     * @return 无全局容器依赖的生成器
     */
    public IdempotentKeyGenerator idempotentKeyGenerator() {
        return new IdempotentKeyGenerator();
    }

    /**
     * 注册按需连接的执行器。
     *
     * @param redis 客户端提供器
     * @param properties 默认窗口及调试配置
     * @return 执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisIdempotentExecutor redisIdempotentExecutor(ObjectProvider<RedissonClient> redis,
                                                          IdempotentProperties properties) {
        return new RedisIdempotentExecutor(redis::getIfAvailable, properties);
    }

    /**
     * 保留原直接传入客户端的配置工厂入口。
     *
     * @param redis 外部管理的客户端
     * @param properties 当前配置
     * @return 执行器
     */
    public RedisIdempotentExecutor redisIdempotentExecutor(RedissonClient redis, IdempotentProperties properties) {
        return new RedisIdempotentExecutor(redis, properties);
    }

    /**
     * 装配可替换切面。
     *
     * @param executor 执行器
     * @param keys 键生成器
     * @param properties 默认配置
     * @return 幂等切面
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(RedisIdempotentExecutor executor, IdempotentKeyGenerator keys, IdempotentProperties properties) {
        return new IdempotentAspect(executor, keys, properties);
    }

    /**
     * 保留原配置工厂方法，使用调用方容器和原键协议。
     *
     * @param executor 幂等执行器
     * @param keys 键生成器
     * @param applicationContext 表达式所属容器
     * @return 使用原构造方式的切面
     */
    public IdempotentAspect idempotentAspect(RedisIdempotentExecutor executor, IdempotentKeyGenerator keys,
                                             ApplicationContext applicationContext) {
        return new IdempotentAspect(executor, keys, applicationContext);
    }
}
