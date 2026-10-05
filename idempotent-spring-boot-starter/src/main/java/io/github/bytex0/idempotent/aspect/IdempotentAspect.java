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
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;

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

    /**
     * 原构造器显式提供的解析器；新构造方式使用生成器自身的当前容器。
     */
    private final BeanFactoryResolver legacyResolver;

    /**
     * 创建使用当前配置与稳定键协议的切面。
     *
     * @param executor 执行器
     * @param keys 键生成器
     * @param properties 默认配置
     */
    public IdempotentAspect(RedisIdempotentExecutor executor, IdempotentKeyGenerator keys, IdempotentProperties properties) {
        this(executor, keys, properties, null);
    }

    /**
     * 保留原 ApplicationContext 构造方式和原键协议，默认窗口来自原执行器。
     *
     * @param executor 执行器
     * @param keys 原键生成器或自定义子类
     * @param applicationContext 表达式 Bean 来源
     */
    public IdempotentAspect(RedisIdempotentExecutor executor, IdempotentKeyGenerator keys,
                            ApplicationContext applicationContext) {
        this(executor, keys, new IdempotentProperties(), new BeanFactoryResolver(applicationContext));
    }

    /**
     * 统一校验和保存切面依赖。
     *
     * @param executor 执行器
     * @param keys 键生成器
     * @param properties 默认配置
     * @param legacyResolver 原构造模式解析器，可为空
     */
    private IdempotentAspect(RedisIdempotentExecutor executor, IdempotentKeyGenerator keys,
                             IdempotentProperties properties, BeanFactoryResolver legacyResolver) {
        Assert.hasText(properties.getKeyPrefix(), "idempotent.key-prefix不能为空");
        Assert.isTrue(properties.getDefaultExpireSeconds() != null && properties.getDefaultExpireSeconds().toMillis() > 0,
                "idempotent.default-expire-seconds必须大于0");
        this.executor = executor;
        this.keys = keys;
        this.properties = properties;
        this.legacyResolver = legacyResolver;
    }

    /**
     * 在受保护作用域中执行业务，不要求额外的 HTTP 映射注解。
     *
     * @param point 当前调用
     * @param annotation 幂等配置
     * @return 业务结果
     * @throws Throwable 业务异常、存储错误或中断
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint point, Idempotent annotation) throws Throwable {
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        String prefix = legacyResolver == null && annotation.keyPrefix().isBlank()
                ? properties.getKeyPrefix() : annotation.keyPrefix();
        String key = legacyResolver == null ? keys.generateInvocationKey(annotation.key(), prefix, point, method)
                : keys.generateKey(annotation.key(), prefix, point, legacyResolver);
        Duration fallback = legacyResolver == null ? properties.getDefaultExpireSeconds() : executor.getDefaultExpireSeconds();
        Duration expiry = annotation.expire() > 0 ? Duration.ofSeconds(annotation.expire()) : fallback;
        return executor.execute(key, expiry, point::proceed);
    }

    /**
     * 保留原切面方法调用入口，实际仍使用成功后记标记的作用域。
     *
     * @param point 当前调用
     * @param annotation 幂等配置
     * @return 业务结果
     * @throws Throwable 业务或存储异常
     */
    public Object checkIdempotent(ProceedingJoinPoint point, Idempotent annotation) throws Throwable {
        return around(point, annotation);
    }
}
