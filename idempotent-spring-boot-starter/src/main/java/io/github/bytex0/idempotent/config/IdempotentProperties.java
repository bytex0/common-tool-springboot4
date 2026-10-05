package io.github.bytex0.idempotent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 幂等配置(IdempotentProperties)成功去重窗口与命名空间
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@Getter
@Setter
@ConfigurationProperties("idempotent")
public class IdempotentProperties {

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 业务命名空间，建议包含应用和租户维度
     */
    private String keyPrefix = "idempotent:";

    /**
     * 成功后去重时长，保留原配置名称
     */
    private Duration defaultExpireSeconds = Duration.ofSeconds(10);
}
