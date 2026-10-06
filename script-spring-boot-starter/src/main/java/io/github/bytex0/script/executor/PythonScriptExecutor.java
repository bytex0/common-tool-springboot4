package io.github.bytex0.script.executor;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.exception.ScriptExecuteException;
import java.util.HashMap;
import java.util.Map;
import org.python.core.Py;
import org.python.core.PyCode;
import org.python.core.PyObject;
import org.python.core.PyStringMap;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.springframework.util.Assert;

/**
 * Jython Python 2.7 执行器，每次调用独立解释器、局部变量和系统状态。
 *
 * @author bytex0
 * @since 2026-10-06 14:01:01
 */
public class PythonScriptExecutor implements ScriptExecutor {

    /**
     * 脚本体的可选结果变量，未定义时返回 null。
     */
    private static final String RESULT_NAME = "result";

    /**
     * {@inheritDoc}
     */
    @Override
    public ScriptType getType() {
        return ScriptType.PYTHON;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object compile(String script) {
        Assert.hasText(script, "Script source is required");
        try (PythonInterpreter interpreter = newInterpreter()) {
            return new CompiledScript(this, interpreter.compile(script));
        } catch (RuntimeException exception) {
            throw new ScriptCompileException("Python compilation failed", exception);
        }
    }

    /**
     * 执行代码对象，修复原实现对编译对象调用 __call__ 的错误。
     *
     * @param compiledScript 本执行器编译产物
     * @param params 非空局部变量参数
     * @return result 变量转换后的 Java 值，未定义或 None 时为 null
     */
    @Override
    public Object executeCompiled(Object compiledScript, Map<String, Object> params) {
        return invoke(compiledScript, null, params);
    }

    /**
     * 执行模块后调用指定函数，传入一个 Map 参数。
     *
     * @param compiledScript 本执行器编译产物
     * @param methodName 非空函数名
     * @param params 非空参数映射
     * @return 函数返回的 Java 值，None 时为 null
     */
    @Override
    public Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.hasText(methodName, "Method name is required");
        return invoke(compiledScript, methodName, params);
    }

    /**
     * 校验产物归属并在一次性解释器中运行。
     *
     * @param compiledScript 编译产物
     * @param methodName 函数名，为空执行模块
     * @param params 绑定参数
     * @return Java 结果，可为空
     */
    private Object invoke(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.notNull(params, "Script parameters are required");
        Assert.isTrue(compiledScript instanceof CompiledScript, "Invalid Python compiled script");
        CompiledScript compiled = (CompiledScript) compiledScript;
        Assert.isTrue(compiled.owner == this, "Compiled script belongs to another executor");
        try (PythonInterpreter interpreter = newInterpreter()) {
            params.forEach(interpreter::set);
            interpreter.exec(compiled.code);
            PyObject result;
            if (methodName == null) {
                result = interpreter.get(RESULT_NAME);
            } else {
                PyObject function = interpreter.get(methodName);
                Assert.isTrue(function != null && function.isCallable(), "Python method does not exist");
                result = function.__call__(Py.java2py(new HashMap<>(params)));
            }
            if (result == null || result == Py.None) {
                return null;
            }
            Object value = result.__tojava__(Object.class);
            if (value == Py.NoConversion) {
                throw new ScriptExecuteException("Python result cannot be converted to Java");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new ScriptExecuteException("Python execution failed", exception);
        }
    }

    /**
     * 创建具有独立模块及局部变量状态的解释器。
     *
     * @return 调用者必须关闭的解释器
     */
    private PythonInterpreter newInterpreter() {
        PySystemState state = new PySystemState();
        state.setClassLoader(getClass().getClassLoader());
        // Boot 可执行 JAR 的嵌套依赖不是普通磁盘目录，通过类路径访问 Jython 标准库。
        state.path.insert(0, Py.newString("__pyclasspath__/Lib"));
        return new PythonInterpreter(new PyStringMap(), state);
    }

    /**
     * Python 代码对象，不包含可变解释器。
     *
     * @author bytex0
     * @since 2026-10-06 14:01:01
     */
    private static final class CompiledScript {

        /**
         * 创建执行器。
         */
        private final PythonScriptExecutor owner;

        /**
         * 编译后的代码对象。
         */
        private final PyCode code;

        /**
         * 保存编译结果。
         *
         * @param owner 创建者
         * @param code 编译代码
         */
        private CompiledScript(PythonScriptExecutor owner, PyCode code) {
            this.owner = owner;
            this.code = code;
        }
    }
}
