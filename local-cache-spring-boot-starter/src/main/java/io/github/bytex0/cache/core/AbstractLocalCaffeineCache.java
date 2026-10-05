package io.github.bytex0.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 本地缓存(AbstractLocalCaffeineCache)支持过期、容量限制与原子加载的基类
 *
 * @author bytex0
 * @since 2026-10-05 15:14:19
 */
public abstract class AbstractLocalCaffeineCache<K, V> {

    /**
     * 延迟初始化的缓存，避免父类构造阶段读取尚未初始化的子类配置
     */
    private volatile Cache<K, V> cache;

    protected abstract Duration getExpireAfterAccess();

    protected abstract long getMaximumSize();

    protected abstract int getInitialCapacity();

    protected void onRemoval(K key, V value, RemovalCause cause) {
    }

    protected Ticker getTicker() {
        return Ticker.systemTicker();
    }

    protected Cache<K, V> createCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(getExpireAfterAccess())
                .maximumSize(getMaximumSize())
                .initialCapacity(getInitialCapacity())
                .ticker(getTicker())
                .recordStats()
                .removalListener(this::onRemoval)
                .build();
    }

    /**
     * 在子类构造完成后初始化；工厂会在所有普通单例创建完成后调用。
     */
    public final void initialize() {
        cache();
    }

    public V get(K key) {
        return cache().getIfPresent(key);
    }

    /**
     * 同一个键的并发缺失查询只执行一次加载；null 或异常不会写入缓存。
     */
    public V get(K key, Supplier<V> supplier) {
        Objects.requireNonNull(supplier, "supplier 不能为空");
        return cache().get(key, ignored -> supplier.get());
    }

    public void put(K key, V value) {
        cache().put(key, value);
    }

    public void remove(K key) {
        cache().invalidate(key);
    }

    public void clear() {
        Cache<K, V> current = cache;
        if (current != null) {
            current.invalidateAll();
            current.cleanUp();
        }
    }

    public long size() {
        return cache().estimatedSize();
    }

    public CacheStats stats() {
        return cache().stats();
    }

    /**
     * 显式执行过期和容量维护，estimatedSize 本身不是强一致统计。
     */
    public void cleanUp() {
        cache().cleanUp();
    }

    private Cache<K, V> cache() {
        Cache<K, V> current = cache;
        if (current == null) {
            synchronized (this) {
                current = cache;
                if (current == null) {
                    current = Objects.requireNonNull(createCache(), "createCache 不能返回 null");
                    cache = current;
                }
            }
        }
        return current;
    }
}
