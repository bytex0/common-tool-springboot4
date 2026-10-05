package io.github.bytex0.idempotent.aspect;

import io.github.bytex0.idempotent.config.IdempotentProperties;
import io.github.bytex0.idempotent.core.IdempotentKeyGenerator;
import io.github.bytex0.idempotent.core.RedisIdempotentExecutor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.Order;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 幂等切面(IdempotentAspect)不再依赖HTTP映射注解
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@Aspect
@Order(-10)
public class IdempotentAspect {

    /**
     * 作用域执行器
     */
    private final RedisIdempotentExecutor executor;

    /**
     * 稳定键生成器
     */
    private final IdempotentKeyGenerator keys;

    /**
     * 默认策略
     */
    private final IdempotentProperties properties;

    public IdempotentAspect(RedisIdempotentExecutor executor, IdempotentKeyGenerator keys, IdempotentProperties properties) {
        Assert.hasText(properties.getKeyPrefix(), "idempotent.key-prefix不能为空");
        Assert.isTrue(properties.getDefaultExpireSeconds() != null && properties.getDefaultExpireSeconds().toMillis() > 0,
                "idempotent.default-expire-seconds必须大于0");
        this.executor = executor; this.keys = keys; this.properties = properties;
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint point, Idempotent annotation) throws Throwable {
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        String prefix = annotation.keyPrefix().isBlank() ? properties.getKeyPrefix() : annotation.keyPrefix();
        String key = keys.generateKey(annotation.key(), prefix, point.getTarget(), method, point.getArgs());
        Duration expiry = annotation.expire() > 0 ? Duration.ofSeconds(annotation.expire()) : properties.getDefaultExpireSeconds();
        return executor.execute(key, expiry, point::proceed);
    }
}
