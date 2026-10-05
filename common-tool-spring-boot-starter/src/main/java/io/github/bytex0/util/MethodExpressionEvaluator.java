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

/**
 * 方法表达式(MethodExpressionEvaluator)仅评估代码中声明的可信SpEL
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
public class MethodExpressionEvaluator {

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

    public MethodExpressionEvaluator(BeanFactory beanFactory) { resolver = new BeanFactoryResolver(beanFactory); }

    public <T> T evaluate(String expression, Object target, Method method, Object[] args, Class<T> type) {
        if (expression == null || expression.isBlank()) { return null; }
        Expression parsed = expressions.get(expression);
        if (parsed == null) {
            parsed = parser.parseExpression(expression);
            if (expressions.size() < 512) { expressions.putIfAbsent(expression, parsed); }
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(target, method, args,
                new DefaultParameterNameDiscoverer());
        context.setVariable("args", args);
        context.setBeanResolver(resolver);
        return parsed.getValue(context, type);
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }
}
