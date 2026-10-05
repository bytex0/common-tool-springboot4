package io.github.bytex0;

import io.github.bytex0.util.MethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 方法表达式(MethodExpressionEvaluatorTest)参数别名及Bean解析测试
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
class MethodExpressionEvaluatorTest {

    @Test
    void shouldResolveNamedAndIndexedArgumentsWithoutEvaluatingTheirContent() throws Exception {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("prefix", "prefix");
        MethodExpressionEvaluator evaluator = new MethodExpressionEvaluator(beans);
        var method = getClass().getDeclaredMethod("operation", String.class);
        assertThat(evaluator.evaluate("@prefix + ':' + #p0", this, method, new Object[]{"key"}, String.class))
                .isEqualTo("prefix:key");
        assertThat(evaluator.evaluate("#key", this, method, new Object[]{"T(System).exit(0)"}, String.class))
                .isEqualTo("T(System).exit(0)");
        assertThat(MethodExpressionEvaluator.digest("key")).hasSize(64);
    }

    public void operation(String key) {
    }
}
