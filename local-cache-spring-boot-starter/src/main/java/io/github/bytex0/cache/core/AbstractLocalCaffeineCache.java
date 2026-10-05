package io.github.bytex0.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 本地缓存(AbstractLocalCaffeineCache)支持过期、容量限制与原子加载的基类
 *
 * @param <K> 非空缓存键类型
 * @param <V> 非空缓存值类型，加载返回 null 时不缓存
 * @author bytex0
 * @since 2026-10-05 15:14:19
 */
public abstract class AbstractLocalCaffeineCache<K, V> {

    /**
     * 延迟初始化的缓存，避免父类构造阶段读取尚未初始化的子类配置
     */
    private volatile Cache<K, V> cache;

    /**
     * 仅保护一次性构造；业务加载及移除回调不在该锁内执行。
     */
    private final ReentrantLock initializationLock = new ReentrantLock();

    /**
     * 检测配置扩展点递归访问当前缓存，受初始化锁保护。
     */
    private boolean initializing;

    /**
     * 返回访问后过期时间，由 Caffeine 校验，零表示立即过期。
     *
     * @return 非空且非负的时长
     */
    protected abstract Duration getExpireAfterAccess();

    /**
     * 返回缓存条目上限。
     *
     * @return 非负条目数，零表示不保留条目
     */
    protected abstract long getMaximumSize();

    /**
     * 返回初始容量，不等同于最大容量。
     *
     * @return 非负初始容量
     */
    protected abstract int getInitialCapacity();

    /**
     * 接收移除通知；默认不处理，原有子类覆盖仍有效，回调时机由 Caffeine 管理。
     *
     * @param key 被移除的键
     * @param value 被移除的值
     * @param cause 移除原因
     */
    protected void onRemoval(K key, V value, RemovalCause cause) {
    }

    /**
     * 提供单调时钟，测试可替换为受控时间。
     *
     * @return Caffeine 使用的时钟
     */
    protected Ticker getTicker() {
        return Ticker.systemTicker();
    }

    /**
     * 在子类初始化完成后创建缓存。为保证一次性创建，此扩展点位于初始化锁内，
     * 不得进行阻塞 I/O、访问当前缓存或形成跨缓存初始化环。
     *
     * @return 非空缓存
     */
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

    /**
     * 获取已有条目，不触发业务加载。
     *
     * @param key 非空缓存键
     * @return 缓存值，缺失时为 null
     */
    public V get(K key) {
        return cache().getIfPresent(key);
    }

    /**
     * 同一个键的并发缺失查询只执行一次加载；null 或异常不会写入缓存。
     *
     * @param key 非空缓存键
     * @param supplier 非空加载器，由 Caffeine 按键协调
     * @return 缓存值或加载结果，可为空
     */
    public V get(K key, Supplier<V> supplier) {
        Objects.requireNonNull(supplier, "supplier 不能为空");
        return cache().get(key, ignored -> supplier.get());
    }

    /**
     * 写入或替换条目。
     *
     * @param key 非空缓存键
     * @param value 非空缓存值
     */
    public void put(K key, V value) {
        cache().put(key, value);
    }

    /**
     * 删除指定条目，缺失时不报错。
     *
     * @param key 非空缓存键
     */
    public void remove(K key) {
        cache().invalidate(key);
    }

    /**
     * 清空已创建缓存，不为清空操作初始化新缓存，也不重置累计统计。
     */
    public void clear() {
        Cache<K, V> current = cache;
        if (current != null) {
            current.invalidateAll();
            current.cleanUp();
        }
    }

    /**
     * 返回 Caffeine 的估算条目数量。
     *
     * @return 估算数量，需要显式维护时先调用 cleanUp
     */
    public long size() {
        return cache().estimatedSize();
    }

    /**
     * 返回不可变累计统计快照。
     *
     * @return 命中、加载和淘汰统计
     */
    public CacheStats stats() {
        return cache().stats();
    }

    /**
     * 显式执行过期和容量维护，estimatedSize 本身不是强一致统计。
     */
    public void cleanUp() {
        cache().cleanUp();
    }

    /**
     * 安全发布单个缓存实例，创建失败允许稍后重试，递归创建立即失败。
     *
     * @return 当前缓存实例
     */
    private Cache<K, V> cache() {
        Cache<K, V> current = cache;
        if (current == null) {
            initializationLock.lock();
            try {
                current = cache;
                if (current == null) {
                    if (initializing) {
                        throw new IllegalStateException("createCache 不能递归访问当前缓存");
                    }
                    initializing = true;
                    try {
                        current = Objects.requireNonNull(createCache(), "createCache 不能返回 null");
                        cache = current;
                    } finally {
                        initializing = false;
                    }
                }
            } finally {
                initializationLock.unlock();
            }
        }
        return current;
    }
}
