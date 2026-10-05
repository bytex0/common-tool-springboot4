package io.github.bytex0.dict.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字典注解(Dict)保留code并追加文本属性
 *
 * @author linshiqiang
 * @since 2026-10-05 16:08:16
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Dict {
    String value();
    String suffix() default "Text";
}
