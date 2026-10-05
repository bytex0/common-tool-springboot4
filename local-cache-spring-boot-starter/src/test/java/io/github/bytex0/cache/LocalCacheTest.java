package io.github.bytex0.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地缓存(LocalCacheTest)初始化、并发加载、容量、过期及失败行为测试
 *
 * @author bytex0
 * @since 2026-10-05 15:16:06
 */
class LocalCacheTest {

    @Test
    void shouldInitializeAfterSubclassConstructorAndOnlyOnce() {
        ConfiguredCache cache = new ConfiguredCache(Duration.ofSeconds(5), 10);
        assertThat(cache.initializations).hasValue(0);
        cache.put("key", "value");
        cache.initialize();
        assertThat(cache.get("key")).isEqualTo("value");
        assertThat(cache.initializations).hasValue(1);
    }

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
}
