package io.github.bytex0.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutorService;

/**
 * 多Redis(MultiRedissonConfig)按需自动配置
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
@AutoConfiguration
@EnableConfigurationProperties(MultiRedisProperties.class)
@ConditionalOnProperty(prefix = "multi-redis", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(RedissonClient.class)
@Import(OtherThreadPoolConfig.class)
public class MultiRedissonConfig {

    /**
     * 提供默认 SDK 客户端工厂。
     *
     * @return 连接工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisClientFactory redisClientFactory() {
        return Redisson::create;
    }

    /**
     * 适配旧配置并建立统一资源管理器。
     *
     * @param properties 已绑定配置
     * @param factory 可替换客户端工厂
     * @param environment 旧配置来源
     * @return 管理器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MultiRedisManager multiRedisManager(MultiRedisProperties properties, RedisClientFactory factory,
                                               Environment environment) {
        RedisConfigurationSupport.resolve(properties, environment);
        return new MultiRedisManager(properties, factory);
    }

    /**
     * 暴露默认客户端，资源仍由管理器独占释放。
     *
     * @param manager 客户端管理器
     * @return 默认客户端
     */
    @Bean(destroyMethod = "")
    @Primary
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(MultiRedisManager manager) {
        return manager.primary();
    }

    /**
     * 保留第二客户端 Bean 名，实际实例由管理器释放。
     *
     * @param manager 共享客户端管理器
     * @param properties 已解析配置
     * @return 备客户端
     */
    @Bean(name = "redissonClient2", destroyMethod = "")
    @ConditionalOnMissingBean(name = "redissonClient2")
    @Conditional(BackupConfigured.class)
    public RedissonClient redissonClient2(MultiRedisManager manager, MultiRedisProperties properties) {
        return manager.get(properties.getBackup());
    }

    /**
     * 保留第三客户端 Bean 名，不自动加入第二机房双写。
     *
     * @param manager 共享客户端管理器
     * @return 第三客户端
     */
    @Bean(name = "redissonClient3", destroyMethod = "")
    @ConditionalOnMissingBean(name = "redissonClient3")
    @Conditional(ThirdConfigured.class)
    public RedissonClient redissonClient3(MultiRedisManager manager) {
        return manager.get("third");
    }

    /**
     * 提供完整工具层，独立转换器不覆盖消费方的全局 JSON Bean。
     *
     * @param manager 管理器
     * @param properties 复制配置
     * @param executor 按原名称可覆盖的执行器
     * @param mappers 消费方或 Boot 配置的 JSON 转换器
     * @return 主备工具
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RedissonUtil redissonUtil(MultiRedisManager manager, MultiRedisProperties properties,
                                    @Qualifier("otherRoomExecutor") ExecutorService executor,
                                    ObjectProvider<ObjectMapper> mappers) {
        ObjectMapper mapper = mappers.getIfAvailable(() -> JsonMapper.builder().build());
        return new RedissonUtil(manager, properties, mapper, executor);
    }

    /**
     * 第二客户端条件(BackupConfigured)兼容旧前缀和显式命名双写目标。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:00:40
     */
    static final class BackupConfigured extends SpringBootCondition {

        /**
         * 按实际配置优先级判断是否存在备库，旧开关不能绕过命名配置覆盖。
         *
         * @param context 条件上下文
         * @param metadata Bean 元数据
         * @return 是否创建备库别名
         */
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return new ConditionOutcome(resolved(context.getEnvironment()).getBackup() != null,
                    "按解析后的备库配置注册别名");
        }
    }

    /**
     * 第三客户端条件(ThirdConfigured)保留两个配置前缀。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:00:40
     */
    static final class ThirdConfigured extends SpringBootCondition {

        /**
         * 按解析后启用的 third 命名连接注册兼容别名。
         *
         * @param context 条件上下文
         * @param metadata Bean 元数据
         * @return 是否创建第三客户端别名
         */
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            MultiRedisProperties.Connection third = resolved(context.getEnvironment()).getClients().get("third");
            return new ConditionOutcome(third != null && third.isEnabled(), "按解析后的第三客户端配置注册别名");
        }
    }

    /**
     * 在条件阶段只解析配置，不实例化 Bean 或访问网络。
     *
     * @param environment 配置环境
     * @return 已按优先级适配的配置
     */
    private static MultiRedisProperties resolved(Environment environment) {
        MultiRedisProperties properties = Binder.get(environment).bind("multi-redis", MultiRedisProperties.class)
                .orElseGet(MultiRedisProperties::new);
        RedisConfigurationSupport.resolve(properties, environment);
        return properties;
    }
}
