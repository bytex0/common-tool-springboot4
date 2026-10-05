package io.github.bytex0.i18n.config;

import io.github.bytex0.i18n.properties.I18nProperties;
import io.github.bytex0.i18n.provider.I18nManager;
import io.github.bytex0.i18n.provider.I18nMessageProvider;
import io.github.bytex0.i18n.provider.InMemoryMessageProvider;
import io.github.bytex0.i18n.provider.ResourceBundleMessageProvider;
import io.github.bytex0.i18n.service.I18nService;
import io.github.bytex0.i18n.source.CustomMessageSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * 国际化(I18nAutoConfiguration)自动配置，核心可用于非Web应用
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
@AutoConfiguration(beforeName = {"org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration"})
@EnableConfigurationProperties(I18nProperties.class)
@ConditionalOnProperty(prefix = "i18n", name = "enabled", havingValue = "true")
public class I18nAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public I18nMessageProvider messageProvider(I18nProperties properties) {
        return switch (properties.getProvider()) {
            case "memory" -> new InMemoryMessageProvider();
            case "resource" -> new ResourceBundleMessageProvider(properties.getBasename(), properties.getCacheSeconds(),
                    Thread.currentThread().getContextClassLoader());
            default -> throw new IllegalArgumentException("i18n.provider只支持memory或resource");
        };
    }

    @Bean("messageSource")
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource messageSource(I18nMessageProvider provider, I18nProperties properties) {
        return new CustomMessageSource(provider, properties.isAlwaysUseMessageFormat(),
                properties.isUseCodeAsDefaultMessage(), I18nManager.parseLocale(properties.getDefaultLocale()));
    }

    @Bean
    @ConditionalOnMissingBean
    public I18nService i18nService(@Qualifier("messageSource") MessageSource source) {
        return new I18nService(source);
    }

    @Bean
    @ConditionalOnMissingBean
    public I18nManager i18nManager(I18nMessageProvider provider) { return new I18nManager(provider); }

    /**
     * Web语言(WebConfiguration)按标准Accept-Language解析请求语言
     *
     * @author bytex0
     * @since 2026-10-05 15:49:14
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(LocaleResolver.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebConfiguration {
        @Bean("localeResolver")
        @ConditionalOnMissingBean(name = "localeResolver")
        LocaleResolver localeResolver(I18nProperties properties) {
            AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
            resolver.setDefaultLocale(I18nManager.parseLocale(properties.getDefaultLocale()));
            return resolver;
        }
    }
}
