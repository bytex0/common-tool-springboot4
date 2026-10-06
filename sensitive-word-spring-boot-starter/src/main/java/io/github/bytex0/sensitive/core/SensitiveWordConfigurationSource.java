package io.github.bytex0.sensitive.core;

/**
 * 配置来源(SensitiveWordConfigurationSource)使两个同名配置入口共享实例，不依赖全限定类型表达式。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:10:08
 */
public interface SensitiveWordConfigurationSource {

    /**
     * 提供当前容器的配置对象。
     *
     * @return 配置
     */
    SensitiveWordOptions options();
}
