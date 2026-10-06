package io.github.bytex0.desensitize.config;

import io.github.bytex0.desensitize.fastjson.DesensitizeValueFilter;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Fastjson 脱敏配置(FastJsonDesensitizeAutoConfiguration)按需暴露过滤器，不修改进程全局状态。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
@AutoConfiguration(after = DesensitizeAutoConfiguration.class)
@ConditionalOnProperty(prefix = "desensitize", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "com.alibaba.fastjson.serializer.ValueFilter")
public class FastJsonDesensitizeAutoConfiguration {

    /**
     * 保留原 Fastjson 1 API 的过滤器 Bean，兼容库使用 Fastjson 2 实现。
     *
     * @param factory 策略工厂
     * @return 显式注册到消费方序列化调用的过滤器
     */
    @Bean
    @ConditionalOnClass(name = "com.alibaba.fastjson.serializer.ValueFilter")
    @ConditionalOnProperty(prefix = "desensitize", name = "enable-fastjson", havingValue = "true")
    @ConditionalOnMissingBean
    public DesensitizeValueFilter desensitizeValueFilter(DesensitizeHandlerFactory factory) {
        return new DesensitizeValueFilter(factory);
    }

}
