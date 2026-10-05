package io.github.bytex0.threadpool;

import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 配置、扩缩容、饱和拒绝及排队任务取消测试。
 *
 * @author bytex0
 * @since 2026-10-05 20:05:22
 */
class ThreadPoolTest {

    @Test
    void configurationSupportsDefaultDisableOverrideAndValidation() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ThreadPoolAutoConfiguration.class));
        runner.run(context -> assertThat(context).hasSingleBean(ThreadPoolRegistry.class));
        runner.withPropertyValues("dynamic-threadpool.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ThreadPoolRegistry.class));
        ThreadPoolRegistry custom = mock(ThreadPoolRegistry.class);
        runner.withBean(ThreadPoolRegistry.class, () -> custom).run(context ->
                assertThat(context.getBean(ThreadPoolRegistry.class)).isSameAs(custom));
        runner.withPropertyValues("dynamic-threadpool.pools.demo.core=4", "dynamic-threadpool.pools.demo.max=1",
                "dynamic-threadpool.pools.demo.capacity=2").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void growsAboveOldMaximumAndShrinksWithoutLosingSettings() throws Exception {
        ThreadPoolProperties properties = new ThreadPoolProperties(Map.of("demo", new ThreadPoolProperties.Settings(1, 1, 2)));
        try (ThreadPoolRegistry registry = new ThreadPoolRegistry(properties, task -> task)) {
            assertThat(registry.resize("demo", 4, 4).core()).isEqualTo(4);
            assertThat(registry.resize("demo", 1, 1).max()).isEqualTo(1);
            assertThatThrownBy(() -> registry.resize("demo", 5, 2)).isInstanceOf(IllegalArgumentException.class);
            assertThat(registry.stats("demo").core()).isEqualTo(1);
            assertThat(registry.submit("demo", () -> 42).get(2, TimeUnit.SECONDS)).isEqualTo(42);
        }
    }

    @Test
    void rejectsSaturationAndCancelsDecoratedQueuedFuture() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        ThreadPoolProperties properties = new ThreadPoolProperties(Map.of("demo", new ThreadPoolProperties.Settings(1, 1, 1)));
        ThreadPoolRegistry registry = new ThreadPoolRegistry(properties, task -> () -> task.run());
        try {
            registry.submit("demo", () -> { entered.countDown(); blocked.await(); return 1; });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = registry.submit("demo", () -> 2);
            assertThatThrownBy(() -> registry.submit("demo", () -> 3)).isInstanceOf(RejectedExecutionException.class);
            assertThat(registry.stats("demo").rejected()).isEqualTo(1);
            registry.close();
            assertThat(queued.isCancelled()).isTrue();
        } finally {
            blocked.countDown();
            registry.close();
        }
    }

    @Test
    void concurrentResizePublishesConsistentConfigurationAndReleasesLockAfterFailure() throws Exception {
        ThreadPoolProperties properties = new ThreadPoolProperties(Map.of("demo", new ThreadPoolProperties.Settings(1, 1, 2)));
        try (ThreadPoolRegistry registry = new ThreadPoolRegistry(properties, task -> task);
             var clients = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<Future<?>>();
            for (int index = 0; index < 60; index++) {
                int size = index % 4 + 1;
                results.add(clients.submit(() -> {
                    registry.resize("demo", size, size);
                    ThreadPoolRegistry.Stats stats = registry.stats("demo");
                    assertThat(stats.core()).isEqualTo(stats.max());
                    assertThatThrownBy(() -> registry.resize("demo", 5, 1))
                            .isInstanceOf(IllegalArgumentException.class);
                }));
            }
            for (Future<?> result : results) {
                result.get(5, TimeUnit.SECONDS);
            }
            assertThat(registry.resize("demo", 2, 2).core()).isEqualTo(2);
        }
    }

    @Test
    void shutdownAllowsWorkerToInspectStateAndRejectsFurtherChanges() throws Exception {
        ThreadPoolProperties properties = new ThreadPoolProperties(Map.of("demo", new ThreadPoolProperties.Settings(1, 1, 2)));
        ThreadPoolRegistry registry = new ThreadPoolRegistry(properties, task -> task);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        try {
            Future<Integer> result = registry.submit("demo", () -> {
                entered.countDown();
                try {
                    blocked.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return registry.stats("demo").core();
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            registry.close();
            assertThat(result.get(2, TimeUnit.SECONDS)).isEqualTo(1);
            assertThatThrownBy(() -> registry.resize("demo", 2, 2)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> registry.submit("demo", () -> 1)).isInstanceOf(RejectedExecutionException.class);
        } finally {
            blocked.countDown();
            registry.close();
        }
    }
}
