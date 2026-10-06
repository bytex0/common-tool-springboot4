package io.github.bytex0.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板、JSON、分片、MDC、缓存回源和并行异常路径测试。
 *
 * @author bytex0
 * @since 2026-10-06 14:47:34
 */
class UtilityCompatibilityTest {

    /**
     * 清理本测试线程的 MDC，不污染后续测试。
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 验证占位符替换不递归、空值按配置处理，二进制参数按 UTF-8 解码。
     */
    @Test
    void formatsWithoutMapOrderOrReplacementInjection() {
        Map<String, Object> values = new HashMap<>();
        values.put("a", "${b}");
        values.put("b", "value");
        values.put("empty", null);
        values.put("bytes", "中文".getBytes(StandardCharsets.UTF_8));
        assertThat(Utils.format("${a}/${b}/${empty}/${unknown}/${bytes}", values, true))
                .isEqualTo("${b}/value/${empty}/${unknown}/中文");
        assertThat(Utils.format("${empty}", values, false)).isEmpty();
        assertThat(Utils.getShardList(List.of(0, 1, 2, 3, 4), 2, 1)).containsExactly(1, 3);
        assertThat(Utils.getShardList(List.of(1), 2, -1)).isEmpty();
        assertThatThrownBy(() -> Utils.getShardList(List.of(1), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证自定义分隔符生效，并保留执行线程的 MDC。
     */
    @Test
    void restoresMdcForDirectExecutionEvenOnFailure() {
        TraceIdUtil.buildAndSetTraceId("|", new Object[]{"request", 2});
        assertThat(TraceIdUtil.getTraceId()).isEqualTo("request|2");
        Map<String, String> before = MDC.getCopyOfContextMap();
        IllegalStateException failure = new IllegalStateException("expected");
        assertThatThrownBy(() -> Utils.execute(() -> {
            MDC.put("extra", "temporary");
            TraceIdUtil.setTraceId("changed");
            throw failure;
        }, (Executor) Runnable::run)).isSameAs(failure);
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(before);
        assertThat(TraceIdUtil.generateTraceId()).matches("[0-9a-f]{32}");
        TraceIdUtil.remove();
        assertThat(TraceIdUtil.getTraceId()).isEmpty();
    }

    /**
     * 验证 JSON 泛型和空字段协议，以及无效 JSON 不返回伪成功 null。
     */
    @Test
    void usesJacksonThreeAndSurfacesParsingErrors() {
        assertThat(JacksonUtil.toObject("[1,2]", new TypeReference<List<Integer>>() { }))
                .containsExactly(1, 2);
        assertThat(JacksonUtil.convertValue(Map.of("value", 3), new TypeReference<Map<String, Integer>>() { }))
                .containsEntry("value", 3);
        assertThat(JacksonUtil.toJson(null)).isEqualTo("null");
        Map<String, Object> nullable = new HashMap<>();
        nullable.put("missing", null);
        nullable.put("present", 1);
        assertThat(JacksonUtil.toJson(nullable)).isEqualTo("{\"present\":1}");
        assertThatThrownBy(() -> JacksonUtil.toObject("{broken", Map.class)).isInstanceOf(RuntimeException.class);
    }

    /**
     * 验证消费失败和任务被拒绝时，调用不会永久等待。
     */
    @Test
    void parallelFailuresAndRejectionReturnToCaller() {
        try (ThreadPoolExecutor pool = newPool()) {
            AtomicInteger completed = new AtomicInteger();
            Utils.consumerParallel(List.of(1, 2, 3), pool, value -> completed.incrementAndGet());
            assertThat(completed.get()).isEqualTo(3);
            IllegalArgumentException failure = new IllegalArgumentException("consumer");
            assertThatThrownBy(() -> Utils.consumerParallel(List.of(1), pool, value -> {
                throw failure;
            })).isSameAs(failure);
            pool.shutdown();
            assertThatThrownBy(() -> Utils.consumerParallel(List.of(1), pool, value -> completed.incrementAndGet()))
                    .isInstanceOf(RejectedExecutionException.class);
        }
    }

    /**
     * 验证显式等待超时，测试结束释放消费任务而不遗留线程。
     */
    @Test
    void parallelTimeoutIsBoundedAndDoesNotOwnExecutor() {
        CountDownLatch release = new CountDownLatch(1);
        try (ThreadPoolExecutor pool = newPool()) {
            try {
                assertThatThrownBy(() -> Utils.consumerParallel(List.of(1), pool, value -> {
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }, 20, TimeUnit.MILLISECONDS)).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("timed out");
                assertThat(pool.isShutdown()).isFalse();
            } finally {
                release.countDown();
            }
        }
    }

    /**
     * 验证缓存命中不回源，缓存故障可回源，数据库异常直接传播。
     */
    @Test
    void cacheReadAndWriteFailuresKeepFallbackContract() {
        FunctionUtil functions = new FunctionUtil();
        AtomicInteger databaseCalls = new AtomicInteger();
        assertThat(functions.getCachedOrLoadDb("test", () -> Optional.of("cached"), () -> {
            databaseCalls.incrementAndGet();
            return Optional.of("db");
        }, value -> databaseCalls.incrementAndGet())).contains("cached");
        assertThat(databaseCalls.get()).isZero();
        assertThat(functions.getCachedOrLoadDb("test", () -> {
            throw new IllegalStateException("cache read");
        }, () -> Optional.of("db"), value -> {
            throw new IllegalStateException("cache write");
        })).contains("db");
        assertThatThrownBy(() -> functions.getCachedOrLoadDb("test", Optional::empty, () -> {
            throw new IllegalArgumentException("database");
        }, value -> databaseCalls.incrementAndGet())).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 创建由测试关闭的有界线程池。
     *
     * @return 测试线程池
     */
    private ThreadPoolExecutor newPool() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());
    }
}
