package io.github.bytex0.config;

import io.github.bytex0.controller.GitInfoController;
import io.github.bytex0.controller.VersionController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 元数据控制器的显式 Web 自动配置，不扫描项目根包，不影响普通非 Web 应用。
 *
 * @author bytex0
 * @since 2026-10-06 18:02:16
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "common-tool", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "common-tool.metadata", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CommonMetadataProperties.class)
public class CommonMetadataAutoConfiguration {

    /**
     * 创建 Git 信息控制器，默认过滤不必要字段。
     *
     * @param resourceLoader 应用资源加载器
     * @param properties 元数据配置
     * @return 可被用户覆盖的控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public GitInfoController gitInfoController(ResourceLoader resourceLoader, CommonMetadataProperties properties) {
        return new GitInfoController(resourceLoader, properties.isIncludeGitDetails(), properties.getMaxResourceBytes());
    }

    /**
     * 创建实例隔离的版本控制器。
     *
     * @param resourceLoader 应用资源加载器
     * @param properties 元数据配置
     * @return 可被用户覆盖的控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public VersionController versionController(ResourceLoader resourceLoader, CommonMetadataProperties properties) {
        return new VersionController(resourceLoader, properties.getMaxResourceBytes());
    }
}
