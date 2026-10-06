package io.github.bytex0.exception;

/**
 * 参数校验异常，保留原版八种构造入口及请求标识。
 *
 * @author bytex0
 * @since 2026-10-06 14:39:06
 */
public class ParamsException extends AbstractRequestException {

    /**
     * Java 序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建无消息参数异常。
     */
    public ParamsException() {
        super(null, null, null);
    }

    /**
     * 创建参数异常。
     *
     * @param message 错误消息
     */
    public ParamsException(String message) {
        super(null, message, null);
    }

    /**
     * 创建关联请求的参数异常。
     *
     * @param requestId 请求标识
     * @param message 错误消息
     */
    public ParamsException(String requestId, String message) {
        super(requestId, message, null);
    }

    /**
     * 创建关联请求及原因的参数异常。
     *
     * @param requestId 请求标识
     * @param message 错误消息
     * @param cause 原始原因
     */
    public ParamsException(String requestId, String message, Throwable cause) {
        super(requestId, message, cause);
    }

    /**
     * 从原始原因创建参数异常。
     *
     * @param cause 原始原因
     */
    public ParamsException(Throwable cause) {
        super(null, causeMessage(cause), cause);
    }

    /**
     * 根据模板创建参数异常。
     *
     * @param messageTemplate {} 占位符模板
     * @param parameters 模板参数
     */
    public ParamsException(String messageTemplate, Object... parameters) {
        super(null, format(messageTemplate, parameters), null);
    }

    /**
     * 创建保留原始原因的参数异常。
     *
     * @param message 错误消息
     * @param cause 原始原因
     */
    public ParamsException(String message, Throwable cause) {
        super(null, message, cause);
    }

    /**
     * 创建保留原因的模板异常。
     *
     * @param cause 原始原因
     * @param messageTemplate {} 占位符模板
     * @param parameters 模板参数
     */
    public ParamsException(Throwable cause, String messageTemplate, Object... parameters) {
        super(null, format(messageTemplate, parameters), cause);
    }
}
