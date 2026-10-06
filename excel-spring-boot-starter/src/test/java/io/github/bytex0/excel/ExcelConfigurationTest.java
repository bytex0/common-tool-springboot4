package io.github.bytex0.excel;

import io.github.bytex0.excel.config.ExcelAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel 配置测试(ExcelConfigurationTest)验证原文档线程池参数真正生效及可选 Web 隔离。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:23:54
 */
class ExcelConfigurationTest {

    /**
     * 线程参数生效，关闭后默认池释放，不依赖 Servlet 类路径。
     */
    @Test
    void shouldBindBoundedPoolAndCloseItWithoutWebDependencies() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader("jakarta.servlet", "org.springframework.web"));
        ThreadPoolExecutor[] reference = new ThreadPoolExecutor[1];
        runner.withPropertyValues("excel.thread-pool.core-size=1", "excel.thread-pool.max-size=2",
                        "excel.thread-pool.queue-capacity=3", "excel.thread-pool.keep-alive-seconds=7")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ExcelTemplate.class);
                    ThreadPoolExecutor pool = context.getBean("excelThreadPool", ThreadPoolExecutor.class);
                    reference[0] = pool;
                    assertThat(pool.getCorePoolSize()).isEqualTo(1);
                    assertThat(pool.getMaximumPoolSize()).isEqualTo(2);
                    assertThat(pool.getQueue().remainingCapacity()).isEqualTo(3);
                    assertThat(pool.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(7);
                });
        assertThat(reference[0].isTerminated()).isTrue();
        runner.withPropertyValues("excel.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ExcelTemplate.class).doesNotHaveBean(ExecutorService.class));
    }

    /**
     * 用户执行器按原 Bean 名覆盖，非法默认容量不能静默通过。
     */
    @Test
    void shouldBackOffToUserExecutorAndRejectInvalidConfiguration() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class));
        try (ExecutorService supplied = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy())) {
            runner.withBean("excelThreadPool", ExecutorService.class, () -> supplied)
                    .run(context -> assertThat(context.getBean("excelThreadPool")).isSameAs(supplied));
        }
        runner.withPropertyValues("excel.thread-pool.queue-capacity=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
