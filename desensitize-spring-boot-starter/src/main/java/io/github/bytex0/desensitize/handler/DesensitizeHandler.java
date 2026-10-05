package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.Desensitize;

/**
 * 脱敏处理器(DesensitizeHandler)业务自定义策略
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
@FunctionalInterface
public interface DesensitizeHandler {
    String desensitize(String value);

    default String desensitize(String value, Desensitize annotation) {
        return desensitize(value);
    }
}
