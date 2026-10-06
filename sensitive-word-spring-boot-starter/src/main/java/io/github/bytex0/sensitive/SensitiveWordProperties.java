package io.github.bytex0.sensitive;

import io.github.bytex0.sensitive.core.SensitiveWordOptions;
import io.github.bytex0.sensitive.core.SensitiveWordConfigurationSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实例隔离的词库和文本匹配配置。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
@ConfigurationProperties("sensitive-word")
public class SensitiveWordProperties extends SensitiveWordOptions implements SensitiveWordConfigurationSource {

    /**
     * 为原包配置视图提供同一实例。
     *
     * @return 当前配置
     */
    @Override
    public SensitiveWordOptions options() {
        return this;
    }
}
