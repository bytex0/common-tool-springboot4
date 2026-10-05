package io.github.bytex0.util;

/**
 * 业务调用(ThrowingSupplier)保留原始受检异常的执行接口
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {
    T get() throws Throwable;
}
