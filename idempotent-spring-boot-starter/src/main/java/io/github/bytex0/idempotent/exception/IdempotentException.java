package io.github.bytex0.idempotent.exception;

/**
 * 幂等冲突(IdempotentException)处理中、已成功或所有权丢失
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
public class IdempotentException extends RuntimeException {
    public IdempotentException(String message) { super(message); }
}
