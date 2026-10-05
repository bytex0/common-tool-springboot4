package io.github.bytex0.lock.aspect;

import io.github.bytex0.lock.enums.LockType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 锁注解(Lock)声明可信SpEL及等待策略
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Lock {
    LockType type() default LockType.REENTRANT_LOCK;
    boolean enable() default true;
    boolean block() default true;
    String key() default "";
    int permits() default 1;
    boolean fair() default true;
    long timeout() default 1000;
    long leaseTime() default 0;
    TimeUnit timeunit() default TimeUnit.MILLISECONDS;
    String permitsFunction() default "";
    String ruleFunction() default "";
}
