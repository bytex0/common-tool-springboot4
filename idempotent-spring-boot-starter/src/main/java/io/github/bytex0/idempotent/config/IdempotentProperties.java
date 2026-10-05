package io.github.bytex0.idempotent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 幂等配置(IdempotentProperties)成功去重窗口与命名空间
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@Data
@ConfigurationProperties("idempotent")
public class IdempotentProperties {

    /**
     * 是否自动装配幂等能力，默认 true。
     */
    private Boolean enabled = true;

    /**
     * 默认业务命名空间，默认 idempotent:，建议包含应用和租户维度。
     */
    private String keyPrefix = "idempotent:";

    /**
     * 默认去重时长，默认 10 秒；显式过期参数不大于零时使用，必须至少 1 毫秒。
     */
    private Duration defaultExpireSeconds = Duration.ofSeconds(10);

    /**
     * 是否记录 DEBUG 级别的操作阶段和时长，默认 false，不记录业务键或参数。
     */
    private Boolean debugLog = false;

    /**
     * 保留便捷判断方法，不破坏原 Boolean JavaBean getter/setter。
     *
     * @return 非空启用状态
     */
    public Boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
