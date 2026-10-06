package io.github.bytex0.desensitize.config;

import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import io.github.bytex0.desensitize.jackson.DesensitizeModule;
import io.github.bytex0.desensitize.util.DesensitizeUtil;
import io.github.bytex0.desensitize.properties.DesensitizeProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

/**
 * 数据脱敏(DesensitizeAutoConfiguration)自动配置
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "desensitize", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DesensitizeProperties.class)
@Import({JacksonDesensitizeAutoConfiguration.class, FastJsonDesensitizeAutoConfiguration.class,
        FastJson2DesensitizeAutoConfiguration.class})
public class DesensitizeAutoConfiguration {

    /**
     * 创建完整处理器工厂，原未实现的 MyBatis 开关不能伪装为生效。
     *
     * @param beanFactory 当前 Bean 容器
     * @param properties 开关属性
     * @return 策略工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public DesensitizeHandlerFactory desensitizeHandlerFactory(ListableBeanFactory beanFactory,
                                                               DesensitizeProperties properties) {
        Assert.isTrue(!Boolean.TRUE.equals(properties.getEnableMybatis()), "当前没有MyBatis脱敏插件实现");
        return new DesensitizeHandlerFactory(beanFactory);
    }

    /**
     * 保留手工模块创建入口，自动 Bean 由 Jackson 子配置提供。
     *
     * @param factory 策略工厂
     * @return Jackson 3 模块
     */
    public DesensitizeModule desensitizeModule(DesensitizeHandlerFactory factory) {
        return new DesensitizeModule(factory);
    }

    /**
     * 创建显式脱敏工具，独立 mapper 不改变全局 Jackson 开关或消费方配置。
     *
     * @param mappers 消费方 Jackson 配置，可选
     * @param factory 策略工厂
     * @return 不依赖全局模块启用状态的显式工具
     */
    @Bean
    @ConditionalOnMissingBean
    public DesensitizeUtil desensitizeUtil(ObjectProvider<JsonMapper> mappers, DesensitizeHandlerFactory factory) {
        JsonMapper mapper = mappers.getIfAvailable(() -> JsonMapper.builder().build());
        JsonMapper isolated = mapper.rebuild().addModule(new DesensitizeModule(factory))
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL)
                        .withContentInclusion(JsonInclude.Include.NON_NULL)).build();
        return new DesensitizeUtil(isolated);
    }
}
