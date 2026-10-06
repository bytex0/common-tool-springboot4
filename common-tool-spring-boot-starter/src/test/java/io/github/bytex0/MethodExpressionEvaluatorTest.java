package io.github.bytex0;

import io.github.bytex0.util.MethodExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 方法表达式(MethodExpressionEvaluatorTest)参数别名及Bean解析测试
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
class MethodExpressionEvaluatorTest {

    /**
     * 验证参数名称、别名和 Bean 引用，不把参数内容再次作为表达式求值。
     *
     * @throws Exception 反射方法获取失败
     */
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

    /**
     * 提供真实参数名称的反射目标，不执行任何业务动作。
     *
     * @param key 业务键
     */
    public void operation(String key) {
    }

    /**
     * 验证高并发不同表达式不会突破缓存上限。
     *
     * @throws Exception 反射检查或并发任务失败
     */
    @Test
    void boundsCacheDuringConcurrentPublication() throws Exception {
        MethodExpressionEvaluator evaluator = new MethodExpressionEvaluator(new DefaultListableBeanFactory());
        var method = getClass().getDeclaredMethod("operation", String.class);
        try (ThreadPoolExecutor workers = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy())) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                int worker = index;
                tasks.add(workers.submit(() -> {
                    for (int number = 0; number < 200; number++) {
                        String value = worker + "-" + number;
                        assertThat(evaluator.evaluate("'" + value + "'", this, method,
                                new Object[]{"key"}, String.class)).isEqualTo(value);
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }
        Field cache = MethodExpressionEvaluator.class.getDeclaredField("expressions");
        cache.setAccessible(true);
        assertThat((Map<?, ?>) cache.get(evaluator)).hasSize(512);
    }
}
