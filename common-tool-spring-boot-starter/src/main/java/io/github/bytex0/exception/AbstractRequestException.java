package io.github.bytex0.exception;

import org.slf4j.helpers.MessageFormatter;

/**
 * 请求类异常的公共状态与消息格式化，保留原因链但不采集调用栈。
 *
 * @author bytex0
 * @since 2026-10-06 14:39:06
 */
public abstract class AbstractRequestException extends RuntimeException {

    /**
     * Java 序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 请求标识，未指定时为 null，不自动从全局上下文读取。
     */
    private final String requestId;

    /**
     * 创建不采集堆栈、允许保留 suppressed 异常的请求异常。
     *
     * @param requestId 请求标识，可为空
     * @param message 错误信息，可为空
     * @param cause 原始原因，可为空
     */
    protected AbstractRequestException(String requestId, String message, Throwable cause) {
        super(message, cause, true, false);
        this.requestId = requestId;
    }

    /**
     * 获取请求标识。
     *
     * @return 请求标识，未设置时为空
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 按顺序替换 {} 占位符，不把最后一个 Throwable 参数隐式移出参数列表。
     *
     * @param template 消息模板，可为空
     * @param parameters 参数，可为空
     * @return 格式化消息
     */
    protected static String format(String template, Object... parameters) {
        return MessageFormatter.arrayFormat(template, parameters, null).getMessage();
    }

    /**
     * 保留原异常仅传原因时的简单类型名与消息格式。
     *
     * @param cause 原始原因，可为空
     * @return 类型及原因消息，原因为空时为空
     */
    protected static String causeMessage(Throwable cause) {
        return cause == null ? null : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
