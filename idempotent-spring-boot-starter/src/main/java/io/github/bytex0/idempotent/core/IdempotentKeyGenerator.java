package io.github.bytex0.idempotent.core;

import io.github.bytex0.util.MethodExpressionEvaluator;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.Assert;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 幂等键(IdempotentKeyGenerator)稳定参数表示与SHA-256命名空间
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
public class IdempotentKeyGenerator {

    /**
     * 可信方法表达式解析器
     */
    private final MethodExpressionEvaluator evaluator;

    /**
     * 不加载出站脱敏/字典模块的确定性参数映射器
     */
    private final JsonMapper canonical = JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    public IdempotentKeyGenerator(BeanFactory beans) { evaluator = new MethodExpressionEvaluator(beans); }

    public String generateKey(String expression, String prefix, Object target, Method method, Object[] args) {
        String logical;
        if (expression == null || expression.isBlank()) {
            logical = method.toGenericString() + "\0" + canonical.writeValueAsString(args);
        } else {
            Object value = evaluator.evaluate(expression, target, method, args, Object.class);
            Assert.isTrue(value instanceof CharSequence || value instanceof Number || value instanceof UUID,
                    "幂等key表达式必须返回稳定标量");
            logical = value.toString();
            Assert.hasText(logical, "幂等key不能为空");
        }
        return MethodExpressionEvaluator.digest(canonical.writeValueAsString(new String[]{prefix, logical}));
    }
}
