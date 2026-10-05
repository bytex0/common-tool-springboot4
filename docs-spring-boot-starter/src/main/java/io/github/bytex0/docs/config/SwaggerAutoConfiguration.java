package io.github.bytex0.docs.config;

import io.github.bytex0.docs.properties.SwaggerProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

/**
 * 接口文档(SwaggerAutoConfiguration)自动配置
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
@AutoConfiguration(beforeName = "org.springdoc.core.configuration.SpringDocConfiguration")
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SwaggerProperties.class)
public class SwaggerAutoConfiguration {

    /**
     * 注册精确来源匹配的上游兼容桥，不因静态后处理器创建而提前实例化配置 Bean。
     *
     * @param beanFactory 当前容器
     * @return Knife4j 兼容后处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public static Knife4jCompatibilityPostProcessor knife4jCompatibilityPostProcessor(
            ConfigurableListableBeanFactory beanFactory) {
        return new Knife4jCompatibilityPostProcessor(beanFactory);
    }

    /**
     * 保留原文档元信息与 Basic scheme 声明，不替换用户自己的 OpenAPI Bean。
     *
     * @param properties 元信息和认证配置
     * @return OpenAPI 模型
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "swagger.enabled", havingValue = "true", matchIfMissing = true)
    public OpenAPI openAPI(SwaggerProperties properties) {
        OpenAPI document = new OpenAPI().info(new Info().title(properties.getTitle()).description(properties.getDescription())
                .version(properties.getVersion()).contact(new Contact().name(properties.getContactName())
                        .email(properties.getContactEmail()).url(properties.getContactUrl())));
        if (properties.isBasicAuth()) {
            document.components(new Components().addSecuritySchemes("basicAuth",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
        }
        return document;
    }

    /**
     * 注册实际保护文档的过滤器，关闭文档时仍保留拦截能力。
     *
     * @param properties 文档访问配置
     * @param environment 当前环境
     * @return 文档访问过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public DocsAccessFilter docsAccessFilter(SwaggerProperties properties, Environment environment) {
        return new DocsAccessFilter(properties, environment);
    }
}
