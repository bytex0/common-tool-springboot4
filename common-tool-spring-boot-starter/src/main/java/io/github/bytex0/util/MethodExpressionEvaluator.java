package io.github.bytex0.util;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 方法表达式(MethodExpressionEvaluator)仅评估代码中声明的可信SpEL
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
public class MethodExpressionEvaluator {

    /**
     * 每个实例最多缓存的表达式数量。
     */
    private static final int MAX_CACHE_SIZE = 512;

    /**
     * 原子限制新增缓存条目，避免并发 size 检查突破容量。
     */
    private final Semaphore cacheSlots = new Semaphore(MAX_CACHE_SIZE);

    /**
     * 当前应用的Bean解析器
     */
    private final BeanFactoryResolver resolver;

    /**
     * 当前实例表达式缓存，限制条目数
     */
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    /**
     * 表达式解析器
     */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /**
     * 使用当前应用 Bean 工厂解析表达式中的 Bean 引用。
     *
     * @param beanFactory 当前应用 Bean 工厂
     */
    public MethodExpressionEvaluator(BeanFactory beanFactory) {
        resolver = new BeanFactoryResolver(beanFactory);
    }

    /**
     * 评估代码中声明的可信表达式，不递归执行参数中的字符串。
     *
     * @param expression 可信 SpEL，为空返回 null
     * @param target 目标对象
     * @param method 被调用方法
     * @param args 方法参数
     * @param type 返回类型
     * @param <T> 返回类型参数
     * @return 表达式结果，可为空
     */
    public <T> T evaluate(String expression, Object target, Method method, Object[] args, Class<T> type) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Expression parsed = expressions.get(expression);
        if (parsed == null) {
            parsed = parser.parseExpression(expression);
            if (cacheSlots.tryAcquire()) {
                Expression existing = expressions.putIfAbsent(expression, parsed);
                if (existing != null) {
                    cacheSlots.release();
                    parsed = existing;
                }
            }
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(target, method, args,
                new DefaultParameterNameDiscoverer());
        context.setVariable("args", args);
        context.setBeanResolver(resolver);
        return parsed.getValue(context, type);
    }

    /**
     * 对业务键生成 SHA-256 摘要。
     *
     * @param value 非空业务键
     * @return 64 位十六进制摘要
     */
    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }
}
