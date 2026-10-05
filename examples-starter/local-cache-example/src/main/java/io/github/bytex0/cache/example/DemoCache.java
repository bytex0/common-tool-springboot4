package io.github.bytex0.cache.example;

import io.github.bytex0.cache.core.AbstractLocalCaffeineCache;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地缓存示例(DemoCache)短期缓存及加载计数
 *
 * @author bytex0
 * @since 2026-10-05 15:14:19
 */
@Component
public class DemoCache extends AbstractLocalCaffeineCache<String, String> {

    /**
     * 缓存未命中时的实际加载次数
     */
    private final AtomicInteger loadCount = new AtomicInteger();

    /**
     * {@inheritDoc}
     */
    @Override
    protected Duration getExpireAfterAccess() {
        return Duration.ofSeconds(2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected long getMaximumSize() {
        return 100;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getInitialCapacity() {
        return 16;
    }

    /**
     * 在缓存缺失时加载内容并增加业务加载计数。
     *
     * @param key 缓存键
     * @param value 缺失时返回的内容
     * @return 最终缓存值
     */
    public String load(String key, String value) {
        return get(key, () -> {
            loadCount.incrementAndGet();
            return value;
        });
    }

    /**
     * 返回实际业务加载次数，不会因清空条目而重置。
     *
     * @return 累计加载次数
     */
    public int getLoadCount() {
        return loadCount.get();
    }
}
