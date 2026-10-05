package io.github.bytex0.lock.exception;

/**
 * 锁异常(LockException)未获取或执行期间失去所有权
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
public class LockException extends RuntimeException {
    public LockException(String message) { super(message); }
}
