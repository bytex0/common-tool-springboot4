package io.github.bytex0.i18n.config;

import io.github.bytex0.i18n.properties.I18nProperties;
import io.github.bytex0.i18n.provider.I18nManager;
import io.github.bytex0.i18n.provider.I18nMessageProvider;
import io.github.bytex0.i18n.provider.InMemoryMessageProvider;
import io.github.bytex0.i18n.provider.ResourceBundleMessageProvider;
import io.github.bytex0.i18n.service.I18nService;
import io.github.bytex0.i18n.source.CustomMessageSource;
import io.github.bytex0.i18n.resolver.CustomLocaleResolver;
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
import org.springframework.util.Assert;

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

    /**
     * 创建内存或资源提供器，拒绝未识别的配置，避免静默选择错误来源。
     *
     * @param properties 当前配置
     * @return 消息提供器
     */
    @Bean
    @ConditionalOnMissingBean
    public I18nMessageProvider messageProvider(I18nProperties properties) {
        Assert.hasText(properties.getProvider(), "i18n.provider不能为空");
        Assert.notNull(properties.getCacheSeconds(), "i18n.cache-seconds不能为空");
        return switch (properties.getProvider()) {
            case "memory" -> new InMemoryMessageProvider();
            case "resource" -> new ResourceBundleMessageProvider(properties.getBasename(), properties.getCacheSeconds(),
                    Thread.currentThread().getContextClassLoader());
            default -> throw new IllegalArgumentException("i18n.provider只支持memory或resource");
        };
    }

    /**
     * 注册标准命名消息源，让 Spring MVC 与业务使用同一套消息。
     *
     * @param provider 当前消息来源
     * @param properties 格式和默认语言配置
     * @return 消息源
     */
    @Bean("messageSource")
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource messageSource(I18nMessageProvider provider, I18nProperties properties) {
        return new CustomMessageSource(provider, Boolean.TRUE.equals(properties.getAlwaysUseMessageFormat()),
                Boolean.TRUE.equals(properties.getUseCodeAsDefaultMessage()),
                I18nManager.parseLocale(properties.getDefaultLocale()));
    }

    /**
     * 注册可覆盖的业务查询入口。
     *
     * @param source 当前命名消息源
     * @return 查询服务
     */
    @Bean
    @ConditionalOnMissingBean
    public I18nService i18nService(@Qualifier("messageSource") MessageSource source) {
        return new I18nService(source);
    }

    /**
     * 注册消息管理入口，不在创建时清空内存数据。
     *
     * @param provider 消息来源
     * @return 管理服务
     */
    @Bean
    @ConditionalOnMissingBean
    public I18nManager i18nManager(I18nMessageProvider provider) {
        return new I18nManager(provider);
    }

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

        /**
         * 仅在 MVC 可用时恢复原解析器 Bean，不给非 Web 应用增加 Web 依赖。
         *
         * @param properties 默认语言配置
         * @return 语言解析器
         */
        @Bean("localeResolver")
        @ConditionalOnMissingBean(name = "localeResolver")
        LocaleResolver localeResolver(I18nProperties properties) {
            return new CustomLocaleResolver(properties.getDefaultLocale());
        }
    }
}
