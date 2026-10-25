package io.github.bytex0.common.log.service;

import io.github.bytex0.common.log.model.OperationLogInfo;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * 可选请求上下文适配边界，使纯 Java 操作日志无需依赖 Servlet 类。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
@FunctionalInterface
public interface OperationRequestAccessor {

    /**
     * 非 Web 调用的无状态适配器。
     */
    OperationRequestAccessor NONE = info -> {
    };

    /**
     * 填充可用请求元数据，不读取请求输入流。
     *
     * @param info 日志模型
     */
    void populate(OperationLogInfo info);

    /**
     * 判断是否为不应序列化的请求基础设施对象。
     *
     * @param argument 方法参数
     * @return 是否应排除，默认 false
     */
    default boolean excludeArgument(Object argument) {
        return false;
    }

    /**
     * 获取已绑定请求体参数的位置。
     *
     * @param method 业务方法
     * @return 参数索引，非 Web 默认空集合
     */
    default Set<Integer> requestBodyIndexes(Method method) {
        return Set.of();
    }
}
