package io.github.bytex0.nacos;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Nacos 显式启用条件，新配置键优先，兼容旧版明确设置的启用开关。
 *
 * @author bytex0
 * @since 2026-10-06 18:24:37
 */
public class NacosEnabledCondition extends SpringBootCondition {

    /**
     * 判断是否存在显式启用授权，不把引入可选依赖当作连接授权。
     *
     * @param context 条件上下文
     * @param metadata 自动配置元数据
     * @return 是否启用
     */
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        String key = environment.containsProperty("common-tool.nacos.enabled")
                ? "common-tool.nacos.enabled" : "spring.cloud.nacos.config.enabled";
        boolean enabled = environment.getProperty(key, Boolean.class, false);
        return enabled ? ConditionOutcome.match("Nacos explicitly enabled")
                : ConditionOutcome.noMatch("Nacos requires explicit opt-in");
    }
}
