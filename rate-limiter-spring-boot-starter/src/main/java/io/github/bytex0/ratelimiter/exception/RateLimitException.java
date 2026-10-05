package io.github.bytex0.ratelimiter.exception;

import org.slf4j.helpers.MessageFormatter;

/**
 * 限流拒绝(RateLimitException)表示当前额度不足
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
public class RateLimitException extends RuntimeException {

    /**
     * 异常序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 可选请求标识，用于关联业务拒绝响应。
     */
    private String requestId;

    /**
     * 创建未指定提示的限流拒绝，保留原模块的无参构造行为。
     */
    public RateLimitException() {
        super();
    }

    /**
     * 创建指定提示的限流拒绝。
     *
     * @param message 业务提示
     */
    public RateLimitException(String message) {
        super(message);
    }

    /**
     * 创建保留原始原因的限流异常。
     *
     * @param cause 原始异常
     */
    public RateLimitException(Throwable cause) {
        super(cause == null ? "null" : cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
    }

    /**
     * 创建指定提示和原因的异常。
     *
     * @param message 业务提示
     * @param cause 原始异常
     */
    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建包含请求标识的业务异常。
     *
     * @param requestId 请求标识
     * @param message 业务提示
     */
    public RateLimitException(String requestId, String message) {
        super(message);
        this.requestId = requestId;
    }

    /**
     * 创建包含请求标识和原因的业务异常。
     *
     * @param requestId 请求标识
     * @param message 业务提示
     * @param cause 原始异常
     */
    public RateLimitException(String requestId, String message, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
    }

    /**
     * 使用 {} 占位符构造提示，调用方不得将敏感参数用于公开响应。
     *
     * @param messageTemplate 提示模板
     * @param parameters 替换参数
     */
    public RateLimitException(String messageTemplate, Object... parameters) {
        super(MessageFormatter.arrayFormat(messageTemplate, parameters, null).getMessage());
    }

    /**
     * 构造格式化提示并保留原始原因。
     *
     * @param cause 原始异常
     * @param messageTemplate 提示模板
     * @param parameters 替换参数
     */
    public RateLimitException(Throwable cause, String messageTemplate, Object... parameters) {
        super(MessageFormatter.arrayFormat(messageTemplate, parameters, null).getMessage(), cause);
    }

    /**
     * 返回可选请求标识。
     *
     * @return 请求标识，未指定时为 null
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 保留原模块业务拒绝不采集堆栈的行为，原始 cause 仍可用于诊断。
     *
     * @return 当前异常实例
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
