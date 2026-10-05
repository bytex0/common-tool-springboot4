package io.github.bytex0.i18n.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国际化(I18nProperties)资源与消息配置
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
@Data
@ConfigurationProperties("i18n")
public class I18nProperties {

    /**
     * 是否启用国际化，默认 false。
     */
    private Boolean enabled = false;

    /**
     * 默认语言，默认 zh_CN，支持 zh_CN 和 zh-CN。
     */
    private String defaultLocale = "zh_CN";

    /**
     * UTF-8 资源基础路径，默认 i18n/messages，可使用斜线或点分隔。
     */
    private String basename = "i18n/messages";

    /**
     * 资源缓存秒数，默认 3600，-1 永久，0 不缓存，不能小于 -1。
     */
    private Integer cacheSeconds = 3600;

    /**
     * 无参数时也应用 MessageFormat，默认 false。
     */
    private Boolean alwaysUseMessageFormat = false;

    /**
     * 未找到消息且未提供默认文本时返回 code，默认 true。
     */
    private Boolean useCodeAsDefaultMessage = true;

    /**
     * 提供器类型，只支持 resource 或 memory，默认 resource。
     */
    private String provider = "resource";

    /**
     * 保留此前版本的判断入口，不破坏 Boolean JavaBean 属性。
     *
     * @return 非空启用状态
     */
    public Boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 返回安全的格式化开关。
     *
     * @return null 配置按 false 处理
     */
    public Boolean isAlwaysUseMessageFormat() {
        return Boolean.TRUE.equals(alwaysUseMessageFormat);
    }

    /**
     * 返回安全的编码回退开关。
     *
     * @return null 配置按 false 处理
     */
    public Boolean isUseCodeAsDefaultMessage() {
        return Boolean.TRUE.equals(useCodeAsDefaultMessage);
    }
}
