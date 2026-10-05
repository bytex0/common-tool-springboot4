package io.github.bytex0.ratelimiter.aspect;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.core.RateLimiterFactory;
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
import java.util.List;

/**
 * 限流切面(RateLimiterAspect)先判断额度再执行服务方法
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@Aspect
@Order(RateLimiterAspect.ASPECT_ORDER)
public class RateLimiterAspect {

    /**
     * 在业务调用前进行额度判断，保持既有切面顺序。
     */
    public static final int ASPECT_ORDER = -20;

    /**
     * 实际策略工厂，允许当前上下文的自定义策略覆盖内置算法。
     */
    private final RateLimiterFactory factory;

    /**
     * 当前上下文表达式求值器
     */
    private final MethodExpressionEvaluator evaluator;

    /**
     * 保留模板构造方式，仅使用模板内置策略。
     *
     * @param template 内置算法模板
     * @param beans 当前 Bean 工厂
     */
    public RateLimiterAspect(RateLimiterTemplate template, BeanFactory beans) {
        this(new RateLimiterFactory(template, List.of()), beans);
    }

    /**
     * 构造支持自定义策略的切面。
     *
     * @param factory 限流策略工厂
     * @param beans 当前 Bean 工厂
     */
    public RateLimiterAspect(RateLimiterFactory factory, BeanFactory beans) {
        this.factory = factory;
        evaluator = new MethodExpressionEvaluator(beans);
    }

    /**
     * 按完整规则、字段表达式、注解常量的优先级执行限流，拒绝时不调用业务。
     *
     * @param point 当前代理调用
     * @param annotation 方法上的限流配置
     * @return 业务方法的原始返回值
     * @throws Throwable 规则求值错误、限流异常或业务方法原始异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint point, RateLimiter annotation) throws Throwable {
        if (!annotation.enable()) {
            return point.proceed();
        }
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
        FlowRule rule = evaluator.evaluate(annotation.ruleFunction(), point.getTarget(), method, point.getArgs(), FlowRule.class);
        if (rule == null) {
            String key = annotation.key().isBlank() ? method.toGenericString()
                    : evaluator.evaluate(annotation.key(), point.getTarget(), method, point.getArgs(), String.class);
            rule = FlowRule.builder().key(key).rateLimiterType(annotation.type())
                    .redisClientType(annotation.redisClientType())
                    .maxRequests(number(annotation.maxRequestsFunction(), annotation.maxRequests(), point, method))
                    .windowTime(number(annotation.windowTimeFunction(), annotation.windowTime(), point, method))
                    .bucketCapacity(number(annotation.bucketCapacityFunction(), annotation.bucketCapacity(), point, method))
                    .tokenRate(number(annotation.tokenRateFunction(), annotation.tokenRate(), point, method))
                    .permits(number(annotation.permitsFunction(), annotation.permits(), point, method)).build();
        }
        if (!factory.tryAccess(rule)) {
            throw new RateLimitException();
        }
        return point.proceed();
    }

    /**
     * 解析可选整数字段表达式，空表达式或 null 结果沿用注解默认值。
     *
     * @param expression 可信 SpEL
     * @param fallback 注解常量
     * @param point 当前调用
     * @param method 实际目标方法
     * @return 求值结果或默认值
     */
    private int number(String expression, int fallback, ProceedingJoinPoint point, Method method) {
        Integer result = evaluator.evaluate(expression, point.getTarget(), method, point.getArgs(), Integer.class);
        return result == null ? fallback : result;
    }
}
