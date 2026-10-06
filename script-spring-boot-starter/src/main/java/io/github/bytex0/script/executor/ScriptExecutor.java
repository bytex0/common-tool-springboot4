package io.github.bytex0.script.executor;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.exception.ScriptValidateException;
import java.util.Map;

/**
 * 原版类型化执行器契约，编译产物由调用者释放，每次执行使用独立可变上下文。
 *
 * @author bytex0
 * @since 2026-10-06 13:18:57
 */
public interface ScriptExecutor {

    /**
     * 获取执行器类型。
     *
     * @return 非空引擎类型
     */
    ScriptType getType();

    /**
     * 编译并按引擎默认语义执行，自动释放临时编译产物。
     *
     * @param script 非空受信源码
     * @param params 非空参数映射，不保证深复制参数对象
     * @return 脚本结果，脚本本身无结果时可为空
     */
    default Object execute(String script, Map<String, Object> params) {
        Object compiled = compile(script);
        try (ScriptResource resource = () -> release(compiled)) {
            return executeCompiled(compiled, params);
        }
    }

    /**
     * 编译并执行指定方法，自动释放临时编译产物。
     *
     * @param script 非空受信源码
     * @param methodName 非空方法名
     * @param params 非空方法参数映射
     * @return 方法结果，可为空
     */
    default Object executeMethod(String script, String methodName, Map<String, Object> params) {
        Object compiled = compile(script);
        try (ScriptResource resource = () -> release(compiled)) {
            return executeCompiledMethod(compiled, methodName, params);
        }
    }

    /**
     * 编译源码而不执行脚本体，调用者负责通过 release 释放产物。
     *
     * @param script 非空受信源码
     * @return 非空编译产物，仅能交给创建它的执行器使用
     * @throws ScriptCompileException 语法或编译失败
     */
    Object compile(String script);

    /**
     * 使用新的执行上下文执行编译产物，不转移产物所有权。
     *
     * @param compiledScript 本执行器创建的未关闭产物
     * @param params 非空参数映射
     * @return 脚本结果，可为空
     */
    Object executeCompiled(Object compiledScript, Map<String, Object> params);

    /**
     * 使用新的执行上下文调用编译产物的方法。
     *
     * @param compiledScript 本执行器创建的未关闭产物
     * @param methodName 非空方法名
     * @param params 非空参数映射
     * @return 方法结果，可为空
     */
    Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params);

    /**
     * 仅验证编译是否成功，不执行脚本，自动释放验证产物。
     *
     * @param script 非空受信源码
     * @throws ScriptValidateException 编译或资源释放失败
     */
    default void validate(String script) {
        try {
            release(compile(script));
        } catch (RuntimeException exception) {
            throw new ScriptValidateException("Script validation failed", exception);
        }
    }

    /**
     * 释放编译产物；调用方必须保证该产物没有正在执行的任务。
     *
     * @param compiledScript 待释放产物，实现 AutoCloseable 时关闭
     * @throws ScriptCompileException 资源关闭失败
     */
    default void release(Object compiledScript) {
        if (compiledScript instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ScriptCompileException("Interrupted while releasing compiled script", exception);
            } catch (Exception exception) {
                throw new ScriptCompileException("Cannot release compiled script", exception);
            }
        }
    }

    /**
     * 无受检异常的资源释放动作，使清理失败通过 suppressed 保留而不覆盖执行异常。
     *
     * @author bytex0
     * @since 2026-10-06 13:55:16
     */
    @FunctionalInterface
    interface ScriptResource extends AutoCloseable {

        /**
         * 释放本次调用拥有的编译资源。
         */
        @Override
        void close();
    }
}
