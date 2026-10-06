package io.github.bytex0;

import io.github.bytex0.config.CommonToolProperties;
import io.github.bytex0.balancer.RandomLoadBalancer;
import io.github.bytex0.balancer.RoundRobinLoadBalancer;
import io.github.bytex0.core.ApplicationInfoInitialize;
import io.github.bytex0.id.IdWorkerUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 通用工具(CommonToolConfiguration)基础自动配置
 *
 * @author bytex0
 * @since 2026-10-05 14:26:50
 */
@AutoConfiguration
@EnableConfigurationProperties(CommonToolProperties.class)
@ConditionalOnProperty(prefix = "common-tool", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CommonToolConfiguration {

    /**
     * 创建应用信息监听器，不扫描其他模块的组件。
     *
     * @param environment 当前应用环境
     * @return 可被用户覆盖的监听器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "common-tool", name = "application-info-enabled",
            havingValue = "true", matchIfMissing = true)
    public ApplicationInfoInitialize applicationInfoInitialize(Environment environment) {
        return new ApplicationInfoInitialize(environment);
    }

    /**
     * 创建实例隔离的 ID 工具，允许用户覆盖或关闭。
     *
     * @param properties 基础配置，节点号范围 0 到 1023
     * @return ID 工具
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "common-tool", name = "id-enabled", havingValue = "true", matchIfMissing = true)
    public IdWorkerUtil idWorkerUtil(CommonToolProperties properties) {
        return new IdWorkerUtil(properties.getWorkerId());
    }

    /**
     * 创建原版默认随机选择器，不进行根包组件扫描。
     *
     * @return 随机选择器
     */
    @Bean
    @ConditionalOnMissingBean(RandomLoadBalancer.class)
    public RandomLoadBalancer<?> randomLoadBalancer() {
        return new RandomLoadBalancer<>();
    }

    /**
     * 创建实例隔离的默认轮询选择器。
     *
     * @return 轮询选择器
     */
    @Bean
    @ConditionalOnMissingBean(RoundRobinLoadBalancer.class)
    public RoundRobinLoadBalancer<?> roundRobinLoadBalancer() {
        return new RoundRobinLoadBalancer<>();
    }
}
