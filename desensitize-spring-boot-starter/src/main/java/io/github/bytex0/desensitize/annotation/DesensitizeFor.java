package io.github.bytex0.desensitize.annotation;

import io.github.bytex0.desensitize.enums.DesensitizeType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 脱敏扩展(DesensitizeFor)声明替换的默认策略
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface DesensitizeFor {

    /**
     * 处理器替换的内置策略；同一类型有多个声明式 Bean 时启动失败，不能依赖扫描顺序。
     *
     * @return 被替换的策略
     */
    DesensitizeType value();
}
