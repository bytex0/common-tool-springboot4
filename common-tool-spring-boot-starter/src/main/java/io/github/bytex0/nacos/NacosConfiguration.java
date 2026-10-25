package io.github.bytex0.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import java.util.Map;
import java.util.Properties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/**
 * 使用独立 Nacos SDK 的 Boot 4 自动配置，不依赖 Boot 3 的 Spring Cloud 适配器。
 *
 * @author bytex0
 * @since 2026-10-06 18:24:37
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.nacos.api.config.ConfigService")
@ConditionalOnProperty(prefix = "common-tool", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(NacosEnabledCondition.class)
@EnableConfigurationProperties(CommonNacosProperties.class)
public class NacosConfiguration {

    /**
     * 创建并拥有默认客户端，应用退出时由 Spring 关闭。
     *
     * @param properties 新 SDK 配置
     * @param environment 用于兼容旧配置键
     * @return 配置客户端
     * @throws NacosException SDK 创建失败
     */
    @Bean(destroyMethod = "shutDown")
    @ConditionalOnMissingBean(ConfigService.class)
    public ConfigService configService(CommonNacosProperties properties, Environment environment) throws NacosException {
        return NacosFactory.createConfigService(assembleProperties(properties, environment));
    }

    /**
     * 仅在存在监听对象时创建监听管理器。
     *
     * @param configs 当前应用的监听对象，Bean 名仅用于收集，不作为 dataId
     * @param properties 请求与队列配置
     * @return 应用关闭时自动清理的监听器
     */
    @Bean
    @ConditionalOnBean(AbstractNacosConfig.class)
    @ConditionalOnMissingBean
    public CommonNacosConfigListener commonNacosConfigListener(Map<String, AbstractNacosConfig> configs,
                                                              CommonNacosProperties properties) {
        return new CommonNacosConfigListener(configs, properties.getTimeout(), properties.getCallbackQueueCapacity());
    }

    /**
     * 合并新字段、旧配置和 SDK 属性，禁止 SDK 输出全部配置项。
     *
     * @param properties 新配置
     * @param environment 旧配置来源
     * @return 不应被记录到日志的 SDK 属性
     */
    Properties assembleProperties(CommonNacosProperties properties, Environment environment) {
        Properties result = new Properties();
        result.putAll(properties.getClientProperties());
        copy(result, PropertyKeyConst.SERVER_ADDR, properties.getServerAddr(),
                environment.getProperty("spring.cloud.nacos.config.server-addr"));
        copy(result, PropertyKeyConst.NAMESPACE, properties.getNamespace(),
                environment.getProperty("spring.cloud.nacos.config.namespace"));
        copy(result, PropertyKeyConst.USERNAME, properties.getUsername(),
                environment.getProperty("spring.cloud.nacos.username",
                        environment.getProperty("spring.cloud.nacos.config.username")));
        copy(result, PropertyKeyConst.PASSWORD, properties.getPassword(),
                environment.getProperty("spring.cloud.nacos.password",
                        environment.getProperty("spring.cloud.nacos.config.password")));
        if (result.getProperty(PropertyKeyConst.SERVER_ADDR, "").isBlank()
                && result.getProperty(PropertyKeyConst.ENDPOINT, "").isBlank()) {
            throw new IllegalArgumentException("Nacos server address or endpoint is required");
        }
        result.setProperty(PropertyKeyConst.LOG_ALL_PROPERTIES, "false");
        return result;
    }

    /**
     * 按新字段优先、旧字段次之、透传属性最后的顺序复制值。
     *
     * @param target 目标属性
     * @param key SDK 键
     * @param explicit 新配置值
     * @param legacy 旧配置值
     */
    private void copy(Properties target, String key, String explicit, String legacy) {
        String value = explicit == null ? legacy : explicit;
        if (value != null) {
            target.setProperty(key, value);
        }
    }
}
