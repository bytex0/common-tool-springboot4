package io.github.bytex0.script.exception;

/**
 * 脚本编译失败，保留底层原因，不包含业务参数。
 *
 * @author bytex0
 * @since 2026-10-06 13:18:57
 */
public class ScriptCompileException extends RuntimeException {

    /**
     * 异常序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建编译异常。
     *
     * @param message 错误说明
     */
    public ScriptCompileException(String message) {
        super(message);
    }

    /**
     * 创建带原始原因的编译异常。
     *
     * @param message 错误说明
     * @param cause 底层原因
     */
    public ScriptCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
