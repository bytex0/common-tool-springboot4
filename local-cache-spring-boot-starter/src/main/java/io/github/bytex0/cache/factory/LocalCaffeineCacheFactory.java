package io.github.bytex0.cache.factory;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.github.bytex0.cache.core.AbstractLocalCaffeineCache;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地缓存(LocalCaffeineCacheFactory)当前应用上下文的命名缓存注册与管理
 *
 * @author bytex0
 * @since 2026-10-05 15:14:19
 */
public class LocalCaffeineCacheFactory implements SmartInitializingSingleton, DisposableBean {

    /**
     * 当前上下文的 Bean 工厂，不使用静态全局引用
     */
    private final ListableBeanFactory beanFactory;

    /**
     * 按 Bean 名称组织的不可修改注册表快照
     */
    private volatile Map<String, AbstractLocalCaffeineCache<?, ?>> caches = Map.of();

    /**
     * 保护注册表发布与关闭状态，不在锁内执行缓存回调。
     */
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    /**
     * 工厂是否已关闭，受生命周期锁保护。
     */
    private boolean closed;

    /**
     * 注入所属容器，不持有跨上下文静态引用。
     *
     * @param beanFactory 可列举单例缓存的工厂
     */
    public LocalCaffeineCacheFactory(ListableBeanFactory beanFactory) {
        Assert.notNull(beanFactory, "Bean工厂不能为空");
        this.beanFactory = beanFactory;
    }

    /**
     * 在普通单例就绪后初始化缓存，关闭后禁止重新发布注册表。
     */
    @Override
    public void afterSingletonsInstantiated() {
        lifecycleLock.lock();
        try {
            Assert.state(!closed, "缓存工厂已关闭");
        } finally {
            lifecycleLock.unlock();
        }
        Map<String, AbstractLocalCaffeineCache<?, ?>> registered = new LinkedHashMap<>();
        for (String name : beanFactory.getBeanNamesForType(AbstractLocalCaffeineCache.class, false, false)) {
            AbstractLocalCaffeineCache<?, ?> cache = beanFactory.getBean(name, AbstractLocalCaffeineCache.class);
            cache.initialize();
            registered.put(name, cache);
        }
        lifecycleLock.lock();
        try {
            Assert.state(!closed, "缓存工厂已关闭");
            caches = Collections.unmodifiableMap(registered);
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * 按类型查找唯一缓存，多个同类型实例时要求调用方按名称选择。
     *
     * @param cacheClass 目标缓存类型
     * @param <T> 缓存具体类型
     * @return 唯一实例，缺失时为空
     */
    public <T extends AbstractLocalCaffeineCache<?, ?>> T getCache(Class<T> cacheClass) {
        Assert.notNull(cacheClass, "cacheClass 不能为空");
        List<T> matches = caches.values().stream().filter(cacheClass::isInstance).map(cacheClass::cast).toList();
        Assert.state(matches.size() <= 1, "存在多个同类型缓存，请按 Bean 名称获取");
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /**
     * 按 Bean 名称获取缓存。
     *
     * @param beanName 注册名称
     * @return 缓存或 null
     */
    public AbstractLocalCaffeineCache<?, ?> getCache(String beanName) {
        return caches.get(beanName);
    }

    /**
     * 返回所有命名缓存，包含同一类型的多个实例。
     *
     * @return 不可修改的注册表快照
     */
    public Map<String, AbstractLocalCaffeineCache<?, ?>> getAllCaches() {
        return caches;
    }

    /**
     * 提供原工厂的按类型索引能力；同类型多实例时明确报错，不静默覆盖。
     *
     * @return 不可修改的类型视图
     */
    public Map<Class<?>, AbstractLocalCaffeineCache<?, ?>> getCachesByType() {
        Map<Class<?>, AbstractLocalCaffeineCache<?, ?>> result = new LinkedHashMap<>();
        for (AbstractLocalCaffeineCache<?, ?> cache : caches.values()) {
            Assert.state(result.putIfAbsent(cache.getClass(), cache) == null, "存在多个同类型缓存，请按名称获取");
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 清空当前快照中的所有缓存，不持有生命周期锁调用缓存方法。
     */
    public void clearAllCaches() {
        caches.values().forEach(AbstractLocalCaffeineCache::clear);
    }

    /**
     * 返回命名缓存的全部累计统计，命中率按固定 Locale 格式化。
     *
     * @return 两层不可修改的统计快照
     */
    public Map<String, Map<String, Object>> getCacheStats() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        caches.forEach((name, cache) -> result.put(name, statistics(cache)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 提供原统计接口按类简单名组织的视图，同名冲突时明确失败。
     *
     * @return 类简单名对应的不可修改统计
     */
    public Map<String, Map<String, Object>> getCacheStatsByClassName() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        caches.values().forEach(cache -> {
            String name = cache.getClass().getSimpleName();
            Assert.state(result.putIfAbsent(name, statistics(cache)) == null, "缓存类名冲突，请按Bean名称查看统计");
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * 收集单个缓存的原有十三项统计，不暴露可修改数据。
     *
     * @param cache 已初始化的缓存
     * @return 不可修改的统计快照
     */
    private Map<String, Object> statistics(AbstractLocalCaffeineCache<?, ?> cache) {
        CacheStats stats = cache.stats();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("size", cache.size());
        values.put("hitCount", stats.hitCount());
        values.put("missCount", stats.missCount());
        values.put("hitRate", percentage(stats.hitRate()));
        values.put("missRate", percentage(stats.missRate()));
        values.put("evictionCount", stats.evictionCount());
        values.put("loadSuccessCount", stats.loadSuccessCount());
        values.put("loadFailureCount", stats.loadFailureCount());
        values.put("totalLoadTime", stats.totalLoadTime());
        values.put("averageLoadPenalty", stats.averageLoadPenalty());
        values.put("loadFailureRate", percentage(stats.loadFailureRate()));
        values.put("requestCount", stats.requestCount());
        values.put("evictionWeight", stats.evictionWeight());
        return Collections.unmodifiableMap(values);
    }

    /**
     * 原子清空注册表，再在锁外清空缓存，重复关闭无副作用。
     */
    @Override
    public void destroy() {
        Map<String, AbstractLocalCaffeineCache<?, ?>> snapshot;
        lifecycleLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            snapshot = caches;
            caches = Map.of();
        } finally {
            lifecycleLock.unlock();
        }
        snapshot.values().forEach(AbstractLocalCaffeineCache::clear);
    }

    /**
     * 将统计比例转换为不受系统语言影响的百分比。
     *
     * @param value 比例值
     * @return 两位小数的百分比
     */
    private String percentage(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100);
    }
}
