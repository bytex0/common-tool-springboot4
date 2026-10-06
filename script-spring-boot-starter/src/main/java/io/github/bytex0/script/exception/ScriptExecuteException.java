package io.github.bytex0.script.exception;

/**
 * 脚本执行失败，区别于编译或校验失败。
 *
 * @author bytex0
 * @since 2026-10-06 13:18:57
 */
public class ScriptExecuteException extends RuntimeException {

    /**
     * 异常序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建执行异常。
     *
     * @param message 错误说明
     */
    public ScriptExecuteException(String message) {
        super(message);
    }

    /**
     * 创建带原始原因的执行异常。
     *
     * @param message 错误说明
     * @param cause 底层原因
     */
    public ScriptExecuteException(String message, Throwable cause) {
        super(message, cause);
    }
}
