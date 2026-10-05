package io.github.bytex0.docs.config;

import io.github.bytex0.docs.properties.SwaggerProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 接口文档(SwaggerAutoConfiguration)自动配置
 *
 * @author linshiqiang
 * @since 2026-10-05 15:29:19
 */
@AutoConfiguration(beforeName = "org.springdoc.core.configuration.SpringDocConfiguration")
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SwaggerProperties.class)
public class SwaggerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "swagger.enabled", havingValue = "true", matchIfMissing = true)
    public OpenAPI openAPI(SwaggerProperties properties) {
        return new OpenAPI().info(new Info().title(properties.getTitle()).description(properties.getDescription())
                .version(properties.getVersion()).contact(new Contact().name(properties.getContactName())
                        .email(properties.getContactEmail()).url(properties.getContactUrl())));
    }

    @Bean
    @ConditionalOnMissingBean
    public DocsAccessFilter docsAccessFilter(SwaggerProperties properties, Environment environment) {
        return new DocsAccessFilter(properties, environment);
    }
}
