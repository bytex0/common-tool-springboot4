package io.github.bytex0.lock.aspect;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.core.LockFactory;
import io.github.bytex0.lock.model.LockRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 锁切面(LockAspect)通过执行模板管理所有权
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@Aspect
public class LockAspect {

    /**
     * 锁执行模板
     */
    private final LockFactory factory;

    /**
     * 当前上下文的表达式解析器
     */
    private final MethodExpressionEvaluator evaluator;

    /**
     * 保留当前模板构造入口。
     *
     * @param template 锁模板
     * @param beanFactory 当前容器
     */
    public LockAspect(LockTemplate template, BeanFactory beanFactory) {
        this(new LockFactory(template, List.of()), beanFactory);
    }

    /**
     * 使用包含用户策略的工厂。
     *
     * @param factory 策略工厂
     * @param beanFactory 当前容器
     */
    public LockAspect(LockFactory factory, BeanFactory beanFactory) {
        this.factory = factory;
        evaluator = new MethodExpressionEvaluator(beanFactory);
    }

    /**
     * 保留原构造器的参数类型与顺序。
     *
     * @param applicationContext 表达式所属容器
     * @param lockFactory 锁策略工厂
     */
    public LockAspect(ApplicationContext applicationContext, LockFactory lockFactory) {
        this(lockFactory, applicationContext);
    }

    /**
     * 完整动态规则优先，正常调用交给作用域管理。
     *
     * @param point 当前调用
     * @param annotation 锁注解
     * @return 业务结果
     * @throws Throwable 业务失败或锁错误
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint point, Lock annotation) throws Throwable {
        if (!annotation.enable() && annotation.ruleFunction().isBlank()) {
            return point.proceed();
        }
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        LockRule rule = evaluator.evaluate(annotation.ruleFunction(), point.getTarget(), method, point.getArgs(), LockRule.class);
        if (rule == null) {
            String key = annotation.key().isBlank() ? method.toGenericString() : "defaultLockKey".equals(annotation.key())
                    ? "defaultLockKey"
                    : evaluator.evaluate(annotation.key(), point.getTarget(), method, point.getArgs(), String.class);
            Integer permits = evaluator.evaluate(annotation.permitsFunction(), point.getTarget(), method, point.getArgs(), Integer.class);
            rule = LockRule.builder().enable(annotation.enable()).key(key).lockType(annotation.type())
                    .redisClientType(annotation.redisClientType()).block(annotation.block())
                    .permits(permits == null ? annotation.permits() : permits).fair(annotation.fair())
                    .timeout(annotation.timeout()).leaseTime(annotation.leaseTime()).timeUnit(annotation.timeunit()).build();
        }
        return factory.execute(rule, point::proceed);
    }
}
