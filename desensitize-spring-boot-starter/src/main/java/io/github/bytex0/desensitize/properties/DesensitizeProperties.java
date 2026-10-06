package io.github.bytex0.desensitize.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 脱敏配置(DesensitizeProperties)保留原功能开关，并明确未实现的 MyBatis 边界。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
@Getter
@Setter
@ConfigurationProperties("desensitize")
public class DesensitizeProperties {

    /**
     * 总开关，沿用本仓库现有默认 true，显式 false 关闭全部自动配置。
     * 原 Boot 3 模型默认 false；升级建议显式配置，避免依赖默认值。
     */
    private Boolean enabled = true;

    /**
     * 是否向应用 Jackson 注册模块，默认 true；不影响显式 DesensitizeUtil 的脱敏转换。
     */
    private Boolean enableJackson = true;

    /**
     * 是否提供 Fastjson 兼容及原生过滤器，默认 false，需相应可选依赖。
     */
    private Boolean enableFastjson = false;

    /**
     * 保留原 MyBatis 开关，默认 false；原仓库无对应插件实现，本版也不宣称已支持。
     * 设为 true 时明确拒绝启动，避免配置看似生效而输出原文。
     */
    private Boolean enableMybatis = false;
}
