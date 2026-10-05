package io.github.bytex0.lock.aspect;

import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.enums.RedisClientType;
import java.lang.annotation.Documented;
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
@Documented
public @interface Lock {

    /**
     * 锁策略，默认当前应用内的可重入锁。
     *
     * @return 锁类型
     */
    LockType type() default LockType.REENTRANT_LOCK;

    /**
     * Redis 客户端选择，默认 Redisson；仅分布式信号量支持 RedisTemplate。
     *
     * @return 客户端类型
     */
    RedisClientType redisClientType() default RedisClientType.REDISSON;

    /**
     * 是否启用，默认 true；非空 ruleFunction 返回的完整规则优先。
     *
     * @return 是否开启
     */
    boolean enable() default true;

    /**
     * 是否等待，默认 true 且最多等待 timeout；false 立即尝试。
     *
     * @return 等待模式
     */
    boolean block() default true;

    /**
     * 可信 SpEL 键表达式，默认保留原 defaultLockKey 字面值；
     * 显式空串使用方法签名。业务建议显式提供隔离的键。
     *
     * @return 键表达式
     */
    String key() default "defaultLockKey";

    /**
     * 信号量总额度，恢复原注解默认 10；必须大于零，permitsFunction 非空结果优先。
     *
     * @return 总额度
     */
    int permits() default 10;

    /**
     * 本地队列是否公平，默认 true；Redis 公平锁由 type 指定。
     *
     * @return 本地公平模式
     */
    boolean fair() default true;

    /**
     * 最大等待时长，默认 1000，单位由 timeunit 指定，必须非负。
     *
     * @return 等待时长
     */
    long timeout() default 1000;

    /**
     * 锁租约，默认 0 使用 watchdog；信号量默认 30 秒并自动续租。
     * 显式信号量租约必须在 1 秒至 1 天内。
     *
     * @return 租约时长
     */
    long leaseTime() default 0;

    /**
     * 等待与租约单位，默认毫秒。
     *
     * @return 时间单位
     */
    TimeUnit timeunit() default TimeUnit.MILLISECONDS;

    /**
     * 动态额度可信 SpEL，支持参数和 Bean；空串或 null 结果回落 permits。
     *
     * @return 返回 Integer 的表达式
     */
    String permitsFunction() default "";

    /**
     * 完整规则可信 SpEL，返回非空 LockRule 时优先于其余注解属性。
     *
     * @return 返回 LockRule 的表达式
     */
    String ruleFunction() default "";
}
