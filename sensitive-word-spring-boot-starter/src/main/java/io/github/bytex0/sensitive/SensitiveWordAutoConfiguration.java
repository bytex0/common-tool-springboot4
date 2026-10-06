package io.github.bytex0.sensitive;

import io.github.bytex0.sensitive.config.SensitiveWordInfrastructure;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

/**
 * 显式资源加载和失败即中止的词库自动配置。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
@AutoConfiguration
@EnableConfigurationProperties(SensitiveWordProperties.class)
@ConditionalOnProperty(prefix = "sensitive-word", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SensitiveWordAutoConfiguration {

    /**
     * 保留当前直接工厂入口及资源加载器参数。
     *
     * @param properties 配置
     * @param loader 资源加载器
     * @return 当前门面
     * @throws IOException 保留原受检异常声明，资源错误保留在异常原因中
     */
    public SensitiveWordService sensitiveWordService(SensitiveWordProperties properties, ResourceLoader loader) throws IOException {
        return new SensitiveWordService(properties, loader);
    }

    /**
     * 默认组件(DefaultService)在用户提供整个根包服务时整体退让。
     *
     * @author linshiqiang
     * @since 2026-10-06 08:51:10
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(SensitiveWordService.class)
    @Import(SensitiveWordInfrastructure.class)
    static class DefaultService {

        /**
         * 为原业务服务提供右开区间兼容门面。
         *
         * @param operations 原业务服务
         * @return 根包门面
         */
        @Bean
        SensitiveWordService sensitiveWordService(SensitiveWordOperations operations) {
            return SensitiveWordService.from(operations);
        }
    }
}
