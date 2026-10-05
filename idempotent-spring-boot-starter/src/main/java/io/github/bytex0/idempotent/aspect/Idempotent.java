package io.github.bytex0.idempotent.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解(Idempotent)适用于通过Spring代理调用的服务方法
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
    String keyPrefix() default "";
    String key() default "";
    long expire() default 0;
}
