package io.github.bytex0.desensitize.annotation;

import io.github.bytex0.desensitize.enums.DesensitizeType;
import io.github.bytex0.desensitize.handler.DesensitizeHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏(Desensitize)出站JSON规则
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Desensitize {
    DesensitizeType type();
    Class<? extends DesensitizeHandler> handler() default DesensitizeHandler.class;
    int startIndex() default 0;
    int endIndex() default -1;
    String maskChar() default "*";
}
