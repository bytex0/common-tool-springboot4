package io.github.bytex0.idempotent.exception;

/**
 * 幂等冲突(IdempotentException)处理中、已成功或所有权丢失
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
public class IdempotentException extends RuntimeException {

    /**
     * 异常序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 可选请求标识。
     */
    private final String requestId;

    /**
     * 原接口提供的消息参数，不在本异常内提前格式化。
     */
    private final Object[] args;

    /**
     * 创建业务冲突。
     *
     * @param message 原始消息或消息编码
     */
    public IdempotentException(String message) {
        this(null, message, (Object[]) null);
    }

    /**
     * 创建包含请求标识的冲突。
     *
     * @param requestId 请求标识
     * @param message 原始消息
     */
    public IdempotentException(String requestId, String message) {
        this(requestId, message, (Object[]) null);
    }

    /**
     * 保留供上层国际化使用的消息参数。
     *
     * @param message 原始消息或编码
     * @param args 消息参数，可为空
     */
    public IdempotentException(String message, Object... args) {
        this(null, message, args);
    }

    /**
     * 创建完整异常并防御性复制参数数组。
     *
     * @param requestId 请求标识
     * @param message 原始消息或编码
     * @param args 消息参数，可为空
     */
    public IdempotentException(String requestId, String message, Object... args) {
        super(message);
        this.requestId = requestId;
        this.args = args == null ? null : args.clone();
    }

    /**
     * 返回原请求标识。
     *
     * @return 请求标识，可为空
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 返回消息参数副本，不暴露可修改的内部数组。
     *
     * @return 参数副本，可为空
     */
    public Object[] getArgs() {
        return args == null ? null : args.clone();
    }

    /**
     * 保留原业务冲突不采集堆栈的行为，不使用内置监视器。
     *
     * @return 当前实例
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
