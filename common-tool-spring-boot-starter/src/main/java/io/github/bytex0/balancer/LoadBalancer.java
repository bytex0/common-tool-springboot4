package io.github.bytex0.balancer;

import java.util.List;

/**
 * 候选集合选择器，调用期间候选列表不得并发修改。
 *
 * @param <T> 候选元素类型
 * @author bytex0
 * @since 2026-10-06 14:46:02
 */
public interface LoadBalancer<T> {

    /**
     * 按业务键选择元素，具体策略定义是否使用业务键。
     *
     * @param key 业务键，轮询策略要求非空
     * @param dataList 候选列表
     * @return 选中的元素；轮询空列表返回 null，随机策略拒绝空列表
     */
    T get(String key, List<T> dataList);

    /**
     * 使用实例默认状态选择元素。
     *
     * @param dataList 候选列表
     * @return 选中的元素；空列表行为由具体策略定义
     */
    T get(List<T> dataList);
}
