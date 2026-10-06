package io.github.bytex0.balancer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 实例隔离的有界轮询选择器，保留先递增后选择的原顺序。
 * 键状态按最近访问淘汰；淘汰后该键从初始位置重新开始。
 *
 * @param <T> 候选元素类型
 * @author bytex0
 * @since 2026-10-06 14:46:02
 */
public class RoundRobinLoadBalancer<T> implements LoadBalancer<T> {

    /**
     * 默认最多保留的业务键数量。
     */
    private static final int DEFAULT_MAX_KEYS = 10000;

    /**
     * 业务键数量限制。
     */
    private final int maxKeys;

    /**
     * 仅保护 keyedIndexes，不在持锁期间读取调用方列表元素。
     */
    private final ReentrantLock stateLock = new ReentrantLock();

    /**
     * 各业务键的轮询位置，按访问顺序排列。
     */
    private final LinkedHashMap<String, Integer> keyedIndexes = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * 不传业务键时使用的实例轮询位置。
     */
    private final AtomicInteger count = new AtomicInteger();

    /**
     * 使用默认有界键容量创建选择器。
     */
    public RoundRobinLoadBalancer() {
        this(DEFAULT_MAX_KEYS);
    }

    /**
     * 指定业务键容量创建选择器。
     *
     * @param maxKeys 正数键容量
     */
    public RoundRobinLoadBalancer(int maxKeys) {
        if (maxKeys <= 0) {
            throw new IllegalArgumentException("maxKeys must be positive");
        }
        this.maxKeys = maxKeys;
    }

    /**
     * 清空本实例业务键状态，不影响其他实例和无键轮询计数。
     */
    public void clear() {
        stateLock.lock();
        try {
            keyedIndexes.clear();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * 按业务键轮询，修复首次访问的空计数器问题。
     *
     * @param key 非空业务键
     * @param dataList 候选列表，为空或空列表时返回 null
     * @return 候选元素或 null
     */
    @Override
    public T get(String key, List<T> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }
        Objects.requireNonNull(key, "key");
        int size = dataList.size();
        int next;
        stateLock.lock();
        try {
            int current = keyedIndexes.getOrDefault(key, 0);
            next = (int) (((long) current + 1) % size);
            keyedIndexes.put(key, next);
            if (keyedIndexes.size() > maxKeys) {
                keyedIndexes.remove(keyedIndexes.keySet().iterator().next());
            }
        } finally {
            stateLock.unlock();
        }
        return dataList.get(next);
    }

    /**
     * 使用实例默认计数轮询，不持有全局共享计数。
     *
     * @param dataList 候选列表，为空或空列表时返回 null
     * @return 候选元素或 null
     */
    @Override
    public T get(List<T> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }
        int size = dataList.size();
        int index = count.updateAndGet(current -> (int) (((long) current + 1) % size));
        return dataList.get(index);
    }
}
