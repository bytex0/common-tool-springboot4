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

/**
 * 本地缓存(LocalCaffeineCacheFactory)当前应用上下文的命名缓存注册与管理
 *
 * @author linshiqiang
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

    public LocalCaffeineCacheFactory(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, AbstractLocalCaffeineCache<?, ?>> registered = new LinkedHashMap<>();
        for (String name : beanFactory.getBeanNamesForType(AbstractLocalCaffeineCache.class, false, false)) {
            AbstractLocalCaffeineCache<?, ?> cache = beanFactory.getBean(name, AbstractLocalCaffeineCache.class);
            cache.initialize();
            registered.put(name, cache);
        }
        caches = Collections.unmodifiableMap(registered);
    }

    public <T extends AbstractLocalCaffeineCache<?, ?>> T getCache(Class<T> cacheClass) {
        Assert.notNull(cacheClass, "cacheClass 不能为空");
        List<T> matches = caches.values().stream().filter(cacheClass::isInstance).map(cacheClass::cast).toList();
        Assert.state(matches.size() <= 1, "存在多个同类型缓存，请按 Bean 名称获取");
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public AbstractLocalCaffeineCache<?, ?> getCache(String beanName) {
        return caches.get(beanName);
    }

    public Map<String, AbstractLocalCaffeineCache<?, ?>> getAllCaches() {
        return caches;
    }

    public void clearAllCaches() {
        caches.values().forEach(AbstractLocalCaffeineCache::clear);
    }

    public Map<String, Map<String, Object>> getCacheStats() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        caches.forEach((name, cache) -> {
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
            result.put(name, Collections.unmodifiableMap(values));
        });
        return Collections.unmodifiableMap(result);
    }

    @Override
    public void destroy() {
        clearAllCaches();
        caches = Map.of();
    }

    private String percentage(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100);
    }
}
