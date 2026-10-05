package io.github.bytex0.lock.aspect;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.model.LockRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;

/**
 * 锁切面(LockAspect)通过执行模板管理所有权
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
@Aspect
public class LockAspect {

    /**
     * 锁执行模板
     */
    private final LockTemplate template;

    /**
     * 当前上下文的表达式解析器
     */
    private final MethodExpressionEvaluator evaluator;

    public LockAspect(LockTemplate template, BeanFactory beanFactory) {
        this.template = template;
        evaluator = new MethodExpressionEvaluator(beanFactory);
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint point, Lock annotation) throws Throwable {
        if (!annotation.enable()) { return point.proceed(); }
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        LockRule rule = evaluator.evaluate(annotation.ruleFunction(), point.getTarget(), method, point.getArgs(), LockRule.class);
        if (rule == null) {
            String key = annotation.key().isBlank() ? method.toGenericString()
                    : evaluator.evaluate(annotation.key(), point.getTarget(), method, point.getArgs(), String.class);
            Integer permits = evaluator.evaluate(annotation.permitsFunction(), point.getTarget(), method, point.getArgs(), Integer.class);
            rule = LockRule.builder().key(key).lockType(annotation.type()).block(annotation.block())
                    .permits(permits == null ? annotation.permits() : permits).fair(annotation.fair())
                    .timeout(annotation.timeout()).leaseTime(annotation.leaseTime()).timeUnit(annotation.timeunit()).build();
        }
        return template.execute(rule, point::proceed);
    }
}
