package io.github.bytex0.threadpool;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * 不可变命名线程池配置，无配置时不创建线程池。
 *
 * @param pools 线程池定义
 * @author bytex0
 * @since 2026-10-05 20:05:22
 */
@ConfigurationProperties("dynamic-threadpool")
public record ThreadPoolProperties(Map<String, Settings> pools) {

    public ThreadPoolProperties {
        pools = pools == null ? Map.of() : Map.copyOf(pools);
        Assert.isTrue(pools.size() <= 32, "At most 32 pools are supported");
    }

    /**
     * 队列容量创建后不变，只允许调整核心和最大线程数。
     *
     * @param core 核心线程数
     * @param max 最大线程数
     * @param capacity 有界队列容量
     * @author bytex0
     * @since 2026-10-05 20:05:22
     */
    public record Settings(int core, int max, int capacity) {
        public Settings {
            Assert.isTrue(core >= 0 && core <= max && max <= 128 && max > 0, "Invalid thread counts");
            Assert.isTrue(capacity > 0 && capacity <= 100000, "Queue capacity must be 1 to 100000");
        }
    }
}
