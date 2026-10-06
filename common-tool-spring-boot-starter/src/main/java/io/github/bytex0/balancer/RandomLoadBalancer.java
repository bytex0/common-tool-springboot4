package io.github.bytex0.balancer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 无共享随机状态的候选选择器，保留原随机策略的空集合拒绝行为。
 *
 * @param <T> 候选元素类型
 * @author bytex0
 * @since 2026-10-06 14:46:02
 */
public class RandomLoadBalancer<T> implements LoadBalancer<T> {

    /**
     * 随机策略不使用业务键。
     *
     * @param key 业务键，可为空且不参与随机计算
     * @param dataList 非空且至少包含一个元素的候选列表
     * @return 候选元素
     */
    @Override
    public T get(String key, List<T> dataList) {
        return get(dataList);
    }

    /**
     * 从候选列表随机选择元素。
     *
     * @param dataList 非空且至少包含一个元素的候选列表
     * @return 候选元素
     */
    @Override
    public T get(List<T> dataList) {
        Objects.requireNonNull(dataList, "dataList");
        if (dataList.isEmpty()) {
            throw new IllegalArgumentException("Candidates must not be empty");
        }
        return dataList.get(ThreadLocalRandom.current().nextInt(dataList.size()));
    }
}
