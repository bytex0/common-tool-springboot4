package io.github.bytex0.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通用工具(CommonToolProperties)基础配置属性
 *
 * @author bytex0
 * @since 2026-10-05 14:26:50
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "common-tool")
public class CommonToolProperties {

    /**
     * 是否启用基础自动配置，不影响工具类直接调用
     */
    private boolean enabled = true;

    /**
     * 是否在应用就绪后输出应用名称与激活环境
     */
    private boolean applicationInfoEnabled = true;
}
