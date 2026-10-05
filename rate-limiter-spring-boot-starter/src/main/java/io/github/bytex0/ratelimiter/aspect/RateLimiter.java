package io.github.bytex0.ratelimiter.aspect;

import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.enums.RedisClientType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
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
@Documented
public @interface RateLimiter {

    /**
     * 限流算法，默认使用实例内 LOCAL；非空 ruleFunction 的结果优先。
     *
     * @return 算法类型
     */
    RateLimiterType type() default RateLimiterType.LOCAL;

    /**
     * 四种 Redis Lua 算法的执行后端，默认 REDISSON；本地和原生 Redisson 算法忽略该项。
     *
     * @return Redis 执行后端
     */
    RedisClientType redisClientType() default RedisClientType.REDISSON;

    /**
     * 是否启用本次注解限流，默认启用；关闭后直接调用业务，不求值规则表达式。
     *
     * @return 是否启用
     */
    boolean enable() default true;

    /**
     * 返回 String 的可信 SpEL 业务键表达式，支持 #p0、#a0、参数名及 @Bean 引用。
     * 默认空串按方法签名隔离，字面量需加单引号；ruleFunction 返回规则时忽略该项。
     *
     * @return 业务键表达式
     */
    String key() default "";

    /**
     * 窗口类算法的最大许可数，默认 10，范围为 1 至 1000000。
     * maxRequestsFunction 返回非空值时覆盖该值。
     *
     * @return 窗口最大许可数
     */
    int maxRequests() default 10;

    /**
     * 窗口长度，单位为秒，默认 1，范围为 1 至 86400；Guava 中表示预热时长并允许为 0。
     * windowTimeFunction 返回非空值时覆盖该值。
     *
     * @return 窗口或预热秒数
     */
    int windowTime() default 1;

    /**
     * 令牌桶或漏桶的最大容量，默认 10，范围为 1 至 1000000。
     * bucketCapacityFunction 返回非空值时覆盖该值。
     *
     * @return 桶容量
     */
    int bucketCapacity() default 10;

    /**
     * 桶算法每秒补充或排出的许可数，也是 Guava 的每秒速率，默认 1。
     * 范围为 1 至 1000000；tokenRateFunction 返回非空值时覆盖该值。
     *
     * @return 每秒许可速率
     */
    int tokenRate() default 1;

    /**
     * 本次请求消耗的许可数，默认 1，范围为 1 至 10000。
     * 窗口和桶算法不能超过对应容量，Guava 不使用窗口容量字段限制单次申请。
     * permitsFunction 返回非空值时覆盖该值。
     *
     * @return 本次许可数
     */
    int permits() default 1;

    /**
     * 最大许可数的可信 SpEL，返回 Integer；默认空串不求值，null 结果沿用 maxRequests。
     *
     * @return 最大许可数表达式
     */
    String maxRequestsFunction() default "";

    /**
     * 窗口或预热秒数的可信 SpEL，返回 Integer；默认空串或 null 结果沿用 windowTime。
     *
     * @return 时间表达式
     */
    String windowTimeFunction() default "";

    /**
     * 桶容量的可信 SpEL，返回 Integer；默认空串或 null 结果沿用 bucketCapacity。
     *
     * @return 容量表达式
     */
    String bucketCapacityFunction() default "";

    /**
     * 每秒速率的可信 SpEL，返回 Integer；默认空串或 null 结果沿用 tokenRate。
     *
     * @return 速率表达式
     */
    String tokenRateFunction() default "";

    /**
     * 请求许可数的可信 SpEL，返回 Integer；默认空串或 null 结果沿用 permits。
     *
     * @return 许可数表达式
     */
    String permitsFunction() default "";

    /**
     * 完整规则的可信 SpEL，返回 FlowRule，优先于其他规则属性；默认空串不求值。
     * 返回 null 时使用注解属性；表达式来自业务代码，不能接受外部请求直接提供的表达式。
     *
     * @return 完整规则表达式
     */
    String ruleFunction() default "";
}
