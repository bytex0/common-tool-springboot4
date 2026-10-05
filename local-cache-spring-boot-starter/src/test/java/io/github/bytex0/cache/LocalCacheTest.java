package io.github.bytex0.cache;

import org.junit.jupiter.api.Test;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.FutureTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地缓存(LocalCacheTest)初始化、并发加载、容量、过期及失败行为测试
 *
 * @author bytex0
 * @since 2026-10-05 15:16:06
 */
class LocalCacheTest {

    /**
     * 子类参数构造完成后才创建缓存，并且只创建一次。
     */
    @Test
    void shouldInitializeAfterSubclassConstructorAndOnlyOnce() {
        ConfiguredCache cache = new ConfiguredCache(Duration.ofSeconds(5), 10);
        assertThat(cache.initializations).hasValue(0);
        cache.put("key", "value");
        cache.initialize();
        assertThat(cache.get("key")).isEqualTo("value");
        assertThat(cache.initializations).hasValue(1);
    }

    /**
     * 访问会延长过期时间，维护后移除已过期条目。
     */
    @Test
    void shouldExpireAfterLastAccessUsingDeterministicClock() {
        ConfiguredCache cache = new ConfiguredCache(Duration.ofSeconds(5), 10);
        cache.put("key", "value");
        cache.clock.addAndGet(Duration.ofSeconds(4).toNanos());
        assertThat(cache.get("key")).isEqualTo("value");
        cache.clock.addAndGet(Duration.ofSeconds(4).toNanos());
        assertThat(cache.get("key")).isEqualTo("value");
        cache.clock.addAndGet(Duration.ofSeconds(6).toNanos());
        assertThat(cache.get("key")).isNull();
        cache.cleanUp();
        assertThat(cache.size()).isZero();
    }

    /**
     * 超过容量时发生真实淘汰并计入统计。
     */
    @Test
    void shouldBoundCapacityAndRecordEvictions() {
        ConfiguredCache cache = new ConfiguredCache(Duration.ofMinutes(1), 2);
        for (int index = 0; index < 20; index++) {
            cache.put("key-" + index, "value");
        }
        cache.cleanUp();
        assertThat(cache.size()).isLessThanOrEqualTo(2);
        assertThat(cache.stats().evictionCount()).isGreaterThanOrEqualTo(18);
    }

    /**
     * 并发缺失查询只执行一次业务加载。
     *
     * @throws Exception 线程协作失败
     */
    @Test
    void shouldLoadMissingKeyOnceAcrossConcurrentCallers() throws Exception {
        ConfiguredCache cache = new ConfiguredCache(Duration.ofMinutes(1), 10);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.get("key", () -> {
                        loads.incrementAndGet();
                        return "loaded";
                    });
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("loaded");
            }
        }
        assertThat(loads).hasValue(1);
        assertThat(cache.initializations).hasValue(1);
        assertThat(cache.stats().loadSuccessCount()).isEqualTo(1);
    }

    /**
     * 空结果和失败加载不污染缓存，后续调用能够恢复。
     */
    @Test
    void shouldNotCacheNullOrFailedLoads() {
        ConfiguredCache cache = new ConfiguredCache(Duration.ofMinutes(1), 10);
        assertThat(cache.get("key", () -> null)).isNull();
        assertThatThrownBy(() -> cache.get("key", () -> {
            throw new IllegalStateException("loader failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cache.get("key", () -> "recovered")).isEqualTo("recovered");
        assertThat(cache.stats().loadFailureCount()).isEqualTo(2);
        assertThat(cache.stats().loadSuccessCount()).isEqualTo(1);
    }

    /**
     * 删除和清空行为保留，命中与缺失统计真实记录。
     */
    @Test
    void shouldSupportRemovalClearAndRealStats() {
        ConfiguredCache cache = new ConfiguredCache(Duration.ofMinutes(1), 10);
        cache.put("a", "value");
        assertThat(cache.get("a")).isEqualTo("value");
        assertThat(cache.get("missing")).isNull();
        assertThat(cache.stats().hitCount()).isEqualTo(1);
        assertThat(cache.stats().missCount()).isEqualTo(1);
        cache.remove("a");
        assertThat(cache.get("a")).isNull();
        cache.put("b", "value");
        cache.clear();
        assertThat(cache.size()).isZero();
    }

    /**
     * 非法容量、时长和空参数不能创建无效缓存。
     */
    @Test
    void shouldRejectInvalidConfigurationAndNullArguments() {
        assertThatThrownBy(() -> new ConfiguredCache(Duration.ofSeconds(-1), 10).initialize())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfiguredCache(Duration.ofSeconds(1), -1).initialize())
                .isInstanceOf(IllegalArgumentException.class);
        ConfiguredCache cache = new ConfiguredCache(Duration.ofSeconds(1), 10);
        assertThatThrownBy(() -> cache.put(null, "value")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cache.get("key", null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * 初始化递归立即失败并释放锁，其他线程随后能够重试初始化。
     *
     * @throws Exception 后续线程执行失败
     */
    @Test
    void shouldReleaseInitializationAfterRecursiveFailure() throws Exception {
        AtomicBoolean recurse = new AtomicBoolean(true);
        ConfiguredCache cache = new ConfiguredCache(Duration.ofMinutes(1), 10) {
            /**
             * 第一次模拟错误的递归配置，之后恢复正常构造。
             *
             * @return 真实缓存
             */
            @Override
            protected Cache<String, String> createCache() {
                if (recurse.getAndSet(false)) {
                    get("recursive");
                }
                return super.createCache();
            }
        };
        assertThatThrownBy(cache::initialize).isInstanceOf(IllegalStateException.class);
        FutureTask<String> retry = new FutureTask<>(() -> cache.get("key", () -> "recovered"));
        Thread thread = Thread.ofPlatform().daemon(true).start(retry);
        try {
            assertThat(retry.get(2, TimeUnit.SECONDS)).isEqualTo("recovered");
            assertThat(cache.initializations).hasValue(1);
        } finally {
            thread.interrupt();
            thread.join(1000);
        }
    }

    /**
     * 原子类的移除回调仍可接收显式删除事件。
     *
     * @throws Exception 等待回调被中断
     */
    @Test
    void shouldInvokeOriginalRemovalCallback() throws Exception {
        CountDownLatch removed = new CountDownLatch(1);
        ConfiguredCache cache = new ConfiguredCache(Duration.ofMinutes(1), 10) {
            /**
             * {@inheritDoc}
             */
            @Override
            protected void onRemoval(String key, String value, RemovalCause cause) {
                if ("key".equals(key) && "value".equals(value) && cause == RemovalCause.EXPLICIT) {
                    removed.countDown();
                }
            }
        };
        cache.put("key", "value");
        cache.remove("key");
        cache.cleanUp();
        assertThat(removed.await(2, TimeUnit.SECONDS)).isTrue();
    }
}
