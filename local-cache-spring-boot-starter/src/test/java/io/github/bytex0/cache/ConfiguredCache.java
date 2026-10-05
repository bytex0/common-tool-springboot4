package io.github.bytex0.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bytex0.cache.core.AbstractLocalCaffeineCache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可控缓存(ConfiguredCache)验证子类构造参数及虚拟时钟的测试实现
 *
 * @author linshiqiang
 * @since 2026-10-05 15:16:06
 */
class ConfiguredCache extends AbstractLocalCaffeineCache<String, String> {

    /**
     * 构造器提供的过期时间
     */
    private final Duration expiry;

    /**
     * 构造器提供的容量上限
     */
    private final long maximumSize;

    /**
     * 测试单调时钟
     */
    final AtomicLong clock = new AtomicLong();

    /**
     * 实际创建 Caffeine 缓存的次数
     */
    final AtomicInteger initializations = new AtomicInteger();

    ConfiguredCache(Duration expiry, long maximumSize) {
        this.expiry = expiry;
        this.maximumSize = maximumSize;
    }

    @Override
    protected Duration getExpireAfterAccess() {
        return expiry;
    }

    @Override
    protected long getMaximumSize() {
        return maximumSize;
    }

    @Override
    protected int getInitialCapacity() {
        return 1;
    }

    @Override
    protected Ticker getTicker() {
        return clock::get;
    }

    @Override
    protected Cache<String, String> createCache() {
        initializations.incrementAndGet();
        return super.createCache();
    }
}
