package io.github.bytex0.desensitize.config;

import io.github.bytex0.desensitize.fastjson.DesensitizeFastjson2ValueFilter;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Fastjson 2 配置(FastJson2DesensitizeAutoConfiguration)在仅有原生库时也能独立提供过滤器。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
@AutoConfiguration(after = DesensitizeAutoConfiguration.class)
@ConditionalOnClass(name = "com.alibaba.fastjson2.filter.ValueFilter")
@ConditionalOnProperty(prefix = "desensitize", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FastJson2DesensitizeAutoConfiguration {

    /**
     * 按需提供原生 API 的过滤器，不启用 AutoType。
     *
     * @param factory 策略工厂
     * @return 原生过滤器
     */
    @Bean
    @ConditionalOnProperty(prefix = "desensitize", name = "enable-fastjson", havingValue = "true")
    @ConditionalOnMissingBean
    public DesensitizeFastjson2ValueFilter desensitizeFastjson2ValueFilter(DesensitizeHandlerFactory factory) {
        return new DesensitizeFastjson2ValueFilter(factory);
    }
}
