package io.github.bytex0.desensitize.config;

import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import io.github.bytex0.desensitize.jackson.DesensitizeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 脱敏配置(JacksonDesensitizeAutoConfiguration)只在两个开关允许时注册属性级模块。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
@AutoConfiguration(after = DesensitizeAutoConfiguration.class)
@ConditionalOnProperty(prefix = "desensitize", name = {"enabled", "enable-jackson"},
        havingValue = "true", matchIfMissing = true)
public class JacksonDesensitizeAutoConfiguration {

    /**
     * 提供可被用户替换的 Jackson 3 模块。
     *
     * @param factory 策略工厂
     * @return 属性级模块
     */
    @Bean
    @ConditionalOnMissingBean
    public DesensitizeModule desensitizeModule(DesensitizeHandlerFactory factory) {
        return new DesensitizeModule(factory);
    }
}
