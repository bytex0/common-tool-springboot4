package io.github.bytex0.ratelimiter.aspect;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.exception.RateLimitException;
import io.github.bytex0.ratelimiter.model.FlowRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * 限流切面(RateLimiterAspect)先判断额度再执行服务方法
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@Aspect
@Order(-20)
public class RateLimiterAspect {

    /**
     * 实际策略执行器
     */
    private final RateLimiterTemplate template;

    /**
     * 当前上下文表达式求值器
     */
    private final MethodExpressionEvaluator evaluator;

    public RateLimiterAspect(RateLimiterTemplate template, BeanFactory beans) {
        this.template = template; evaluator = new MethodExpressionEvaluator(beans);
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint point, RateLimiter annotation) throws Throwable {
        if (!annotation.enable()) { return point.proceed(); }
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        FlowRule rule = evaluator.evaluate(annotation.ruleFunction(), point.getTarget(), method, point.getArgs(), FlowRule.class);
        if (rule == null) {
            String key = annotation.key().isBlank() ? method.toGenericString()
                    : evaluator.evaluate(annotation.key(), point.getTarget(), method, point.getArgs(), String.class);
            rule = FlowRule.builder().key(key).rateLimiterType(annotation.type())
                    .maxRequests(number(annotation.maxRequestsFunction(), annotation.maxRequests(), point, method))
                    .windowTime(number(annotation.windowTimeFunction(), annotation.windowTime(), point, method))
                    .bucketCapacity(number(annotation.bucketCapacityFunction(), annotation.bucketCapacity(), point, method))
                    .tokenRate(number(annotation.tokenRateFunction(), annotation.tokenRate(), point, method))
                    .permits(number(annotation.permitsFunction(), annotation.permits(), point, method)).build();
        }
        if (!template.tryAccess(rule)) { throw new RateLimitException(); }
        return point.proceed();
    }

    private int number(String expression, int fallback, ProceedingJoinPoint point, Method method) {
        Integer result = evaluator.evaluate(expression, point.getTarget(), method, point.getArgs(), Integer.class);
        return result == null ? fallback : result;
    }
}
