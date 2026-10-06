package io.github.bytex0.util;

/**
 * 业务调用(ThrowingSupplier)保留原始受检异常的执行接口
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {
    /**
     * 执行业务调用并保留原始异常。
     *
     * @return 业务结果，可为空
     * @throws Throwable 业务调用失败
     */
    T get() throws Throwable;
}
