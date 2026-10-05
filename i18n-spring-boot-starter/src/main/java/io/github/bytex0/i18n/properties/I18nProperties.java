package io.github.bytex0.i18n.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国际化(I18nProperties)资源与消息配置
 *
 * @author linshiqiang
 * @since 2026-10-05 15:49:14
 */
@Getter
@Setter
@ConfigurationProperties("i18n")
public class I18nProperties {

    /**
     * 是否启用国际化
     */
    private boolean enabled;

    /**
     * 默认语言，支持zh_CN和zh-CN
     */
    private String defaultLocale = "zh_CN";

    /**
     * UTF-8资源基础路径
     */
    private String basename = "i18n/messages";

    /**
     * 资源缓存秒数，-1永久，0不缓存
     */
    private int cacheSeconds = 3600;

    /**
     * 无参数时也应用MessageFormat
     */
    private boolean alwaysUseMessageFormat;

    /**
     * 未找到消息且未提供默认文本时返回code
     */
    private boolean useCodeAsDefaultMessage = true;

    /**
     * 提供器类型：resource或memory
     */
    private String provider = "resource";
}
