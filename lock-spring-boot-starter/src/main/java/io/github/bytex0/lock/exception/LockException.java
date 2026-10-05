package io.github.bytex0.lock.exception;

import org.slf4j.helpers.MessageFormatter;

/**
 * 锁异常(LockException)未获取或执行期间失去所有权
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
public class LockException extends RuntimeException {

    /**
     * 异常序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 可选请求标识。
     */
    private final String requestId;

    /**
     * 创建无消息异常。
     */
    public LockException() {
        this((String) null, null, null);
    }

    /**
     * 创建锁业务异常。
     *
     * @param message 错误消息
     */
    public LockException(String message) {
        this((String) null, message, null);
    }

    /**
     * 保留原请求标识构造方式。
     *
     * @param requestId 请求标识
     * @param message 错误消息
     */
    public LockException(String requestId, String message) {
        this(requestId, message, null);
    }

    /**
     * 保留原完整异常构造器。
     *
     * @param requestId 请求标识
     * @param message 错误消息
     * @param cause 原始原因
     */
    public LockException(String requestId, String message, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
    }

    /**
     * 保留原消息与原因构造器。
     *
     * @param message 错误消息
     * @param cause 原始原因
     */
    public LockException(String message, Throwable cause) {
        this(null, message, cause);
    }

    /**
     * 用原因类型与消息构造异常，保留原因链。
     *
     * @param cause 原始原因，可为空
     */
    public LockException(Throwable cause) {
        this(null, cause == null ? "null" : cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
    }

    /**
     * 使用花括号占位符构造错误消息。
     *
     * @param template 消息模板
     * @param params 模板参数
     */
    public LockException(String template, Object... params) {
        this((String) null, MessageFormatter.arrayFormat(template, params, null).getMessage(), null);
    }

    /**
     * 使用占位符消息并保留原因。
     *
     * @param cause 原始原因
     * @param template 消息模板
     * @param params 模板参数
     */
    public LockException(Throwable cause, String template, Object... params) {
        this(null, MessageFormatter.arrayFormat(template, params, null).getMessage(), cause);
    }

    /**
     * 获取原请求标识。
     *
     * @return 请求标识，可为空
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 保持原业务异常不采集堆栈的语义，不使用内置监视器。
     *
     * @return 当前实例
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
