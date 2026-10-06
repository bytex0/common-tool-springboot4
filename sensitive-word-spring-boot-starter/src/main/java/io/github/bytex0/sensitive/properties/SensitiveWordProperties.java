package io.github.bytex0.sensitive.properties;

import io.github.bytex0.sensitive.core.SensitiveWordOptions;
import lombok.experimental.Delegate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 原配置入口(SensitiveWordProperties)保留原字段、访问器和默认值。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@ConfigurationProperties("sensitive-word")
public class SensitiveWordProperties extends SensitiveWordOptions {

    /**
     * 访问器委托到同一容器配置；独立构造时拥有自己的配置，不存储静态引用。
     */
    @Delegate
    private final SensitiveWordOptions source;

    /**
     * 保留原无参配置构造方式。
     */
    public SensitiveWordProperties() {
        this(new SensitiveWordOptions());
    }

    /**
     * 为现有根包配置提供原包类型视图。
     *
     * @param source 共享配置
     */
    public SensitiveWordProperties(SensitiveWordOptions source) {
        this.source = source;
    }
}
