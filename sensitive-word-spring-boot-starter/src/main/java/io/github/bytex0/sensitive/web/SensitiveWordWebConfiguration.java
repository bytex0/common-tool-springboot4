package io.github.bytex0.sensitive.web;

import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.properties.SensitiveWordProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web装配(SensitiveWordWebConfiguration)仅在 Servlet 应用显式启用时注册，不影响纯文本使用方。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:10:08
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {"jakarta.servlet.Filter", "tools.jackson.databind.json.JsonMapper"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "sensitive-word.web", name = "enabled", havingValue = "true")
public class SensitiveWordWebConfiguration {

    /**
     * 以普通 Filter Bean 交给 Boot Servlet 自动注册。
     *
     * @param operations 共享业务组件
     * @param properties 配置
     * @return 请求过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordWebFilter sensitiveWordWebFilter(SensitiveWordOperations operations, SensitiveWordProperties properties) {
        return new SensitiveWordWebFilter(operations, properties);
    }
}
