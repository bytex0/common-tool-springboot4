package io.github.bytex0.desensitize.config;

import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import io.github.bytex0.desensitize.jackson.DesensitizeModule;
import io.github.bytex0.desensitize.util.DesensitizeUtil;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * 数据脱敏(DesensitizeAutoConfiguration)自动配置
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "desensitize", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DesensitizeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DesensitizeHandlerFactory desensitizeHandlerFactory(ListableBeanFactory beanFactory) {
        return new DesensitizeHandlerFactory(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public DesensitizeModule desensitizeModule(DesensitizeHandlerFactory factory) {
        return new DesensitizeModule(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public DesensitizeUtil desensitizeUtil(JsonMapper mapper) { return new DesensitizeUtil(mapper); }
}
