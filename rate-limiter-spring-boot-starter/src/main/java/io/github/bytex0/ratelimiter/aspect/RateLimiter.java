package io.github.bytex0.ratelimiter.aspect;

import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解(RateLimiter)可信规则表达式
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimiter {
    RateLimiterType type() default RateLimiterType.LOCAL;
    boolean enable() default true;
    String key() default "";
    int maxRequests() default 10;
    int windowTime() default 1;
    int bucketCapacity() default 10;
    int tokenRate() default 1;
    int permits() default 1;
    String maxRequestsFunction() default "";
    String windowTimeFunction() default "";
    String bucketCapacityFunction() default "";
    String tokenRateFunction() default "";
    String permitsFunction() default "";
    String ruleFunction() default "";
}
