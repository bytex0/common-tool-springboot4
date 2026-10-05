package io.github.bytex0;

import io.github.bytex0.config.CommonToolProperties;
import io.github.bytex0.core.ApplicationInfoInitialize;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 通用工具(CommonToolConfiguration)基础自动配置
 *
 * @author linshiqiang
 * @since 2026-10-05 14:26:50
 */
@AutoConfiguration
@EnableConfigurationProperties(CommonToolProperties.class)
@ConditionalOnProperty(prefix = "common-tool", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CommonToolConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "common-tool", name = "application-info-enabled",
            havingValue = "true", matchIfMissing = true)
    public ApplicationInfoInitialize applicationInfoInitialize(Environment environment) {
        return new ApplicationInfoInitialize(environment);
    }
}
