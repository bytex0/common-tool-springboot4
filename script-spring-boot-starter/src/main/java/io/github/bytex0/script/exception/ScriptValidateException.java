package io.github.bytex0.script.exception;

/**
 * 脚本语法校验失败；校验成功不代表脚本可被安全执行。
 *
 * @author bytex0
 * @since 2026-10-06 13:18:57
 */
public class ScriptValidateException extends RuntimeException {

    /**
     * 异常序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建校验异常。
     *
     * @param message 错误说明
     */
    public ScriptValidateException(String message) {
        super(message);
    }

    /**
     * 创建带原始原因的校验异常。
     *
     * @param message 错误说明
     * @param cause 底层原因
     */
    public ScriptValidateException(String message, Throwable cause) {
        super(message, cause);
    }
}
