package io.github.bytex0.exception;

/**
 * 底层业务请求异常，保留原版八种构造入口及请求标识。
 *
 * @author bytex0
 * @since 2026-10-06 14:39:06
 */
public class BaseRequestException extends AbstractRequestException {

    /**
     * Java 序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建无消息请求异常。
     */
    public BaseRequestException() {
        super(null, null, null);
    }

    /**
     * 创建请求异常。
     *
     * @param message 错误消息
     */
    public BaseRequestException(String message) {
        super(null, message, null);
    }

    /**
     * 创建关联请求的异常。
     *
     * @param requestId 请求标识
     * @param message 错误消息
     */
    public BaseRequestException(String requestId, String message) {
        super(requestId, message, null);
    }

    /**
     * 创建关联请求及原因的异常。
     *
     * @param requestId 请求标识
     * @param message 错误消息
     * @param cause 原始原因
     */
    public BaseRequestException(String requestId, String message, Throwable cause) {
        super(requestId, message, cause);
    }

    /**
     * 从原始原因创建请求异常。
     *
     * @param cause 原始原因
     */
    public BaseRequestException(Throwable cause) {
        super(null, causeMessage(cause), cause);
    }

    /**
     * 根据模板创建请求异常。
     *
     * @param messageTemplate {} 占位符模板
     * @param parameters 模板参数
     */
    public BaseRequestException(String messageTemplate, Object... parameters) {
        super(null, format(messageTemplate, parameters), null);
    }

    /**
     * 创建保留原始原因的请求异常。
     *
     * @param message 错误消息
     * @param cause 原始原因
     */
    public BaseRequestException(String message, Throwable cause) {
        super(null, message, cause);
    }

    /**
     * 创建保留原因的模板异常。
     *
     * @param cause 原始原因
     * @param messageTemplate {} 占位符模板
     * @param parameters 模板参数
     */
    public BaseRequestException(Throwable cause, String messageTemplate, Object... parameters) {
        super(null, format(messageTemplate, parameters), cause);
    }
}
