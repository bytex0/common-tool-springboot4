package io.github.bytex0.script.executor;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.exception.ScriptExecuteException;
import java.io.StringReader;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.springframework.util.Assert;

/**
 * LuaJ 执行器，编译原型可复用，每次执行创建独立 Globals 并保留字符串返回协议。
 *
 * @author bytex0
 * @since 2026-10-06 14:01:01
 */
public class LuaScriptExecutor implements ScriptExecutor {

    /**
     * 是否移除系统、文件和 Java 互操作库；不等同于进程级安全隔离。
     */
    private final boolean sandbox;

    /**
     * 默认启用受限标准库。
     */
    public LuaScriptExecutor() {
        this(true);
    }

    /**
     * 设置标准库限制。
     *
     * @param sandbox true 时移除文件、系统、模块加载和 Java 互操作入口
     */
    public LuaScriptExecutor(boolean sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScriptType getType() {
        return ScriptType.LUA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object compile(String script) {
        Assert.hasText(script, "Script source is required");
        try {
            Prototype prototype = newGlobals().compilePrototype(new StringReader(script), "script");
            return new CompiledScript(this, prototype);
        } catch (Exception exception) {
            throw new ScriptCompileException("Lua compilation failed", exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object executeCompiled(Object compiledScript, Map<String, Object> params) {
        return invoke(compiledScript, null, params);
    }

    /**
     * 指定方法接收一个参数表，表内值沿用原版字符串转换约定。
     *
     * @param compiledScript 本执行器编译产物
     * @param methodName 非空全局函数名
     * @param params 非空参数映射
     * @return 第一个返回值的 Lua 字符串表示
     */
    @Override
    public Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.hasText(methodName, "Method name is required");
        return invoke(compiledScript, methodName, params);
    }

    /**
     * 在独立环境执行脚本体或函数。
     *
     * @param compiledScript 编译产物
     * @param methodName 函数名，为空执行脚本体
     * @param params 参数映射
     * @return 原版字符串结果
     */
    private Object invoke(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.notNull(params, "Script parameters are required");
        Assert.isTrue(compiledScript instanceof CompiledScript, "Invalid Lua compiled script");
        CompiledScript compiled = (CompiledScript) compiledScript;
        Assert.isTrue(compiled.owner == this, "Compiled script belongs to another executor");
        Globals globals = newGlobals();
        LuaTable arguments = new LuaTable();
        params.forEach((key, value) -> {
            LuaValue converted = value == null ? LuaValue.NIL : LuaValue.valueOf(value.toString());
            globals.set(key, converted);
            arguments.set(key, converted);
        });
        try {
            LuaValue result = new LuaClosure(compiled.prototype, globals).call();
            if (methodName != null) {
                result = globals.get(methodName).checkfunction().call(arguments);
            }
            return result.tojstring();
        } catch (RuntimeException exception) {
            throw new ScriptExecuteException("Lua execution failed", exception);
        }
    }

    /**
     * 创建独立标准库环境并注入指令级中断检查。
     *
     * @return 本次调用拥有的环境
     */
    private Globals newGlobals() {
        Globals globals = JsePlatform.standardGlobals();
        globals.load(new InterruptDebugLib());
        if (sandbox) {
            for (String name : new String[]{"os", "io", "luajava", "package", "require", "dofile", "loadfile", "debug"}) {
                globals.set(name, LuaValue.NIL);
            }
        }
        return globals;
    }

    /**
     * 不持有可变运行环境的编译产物。
     *
     * @author bytex0
     * @since 2026-10-06 14:01:01
     */
    private static final class CompiledScript {

        /**
         * 创建执行器。
         */
        private final LuaScriptExecutor owner;

        /**
         * 只读复用的字节码原型。
         */
        private final Prototype prototype;

        /**
         * 保存编译结果。
         *
         * @param owner 创建者
         * @param prototype 字节码原型
         */
        private CompiledScript(LuaScriptExecutor owner, Prototype prototype) {
            this.owner = owner;
            this.prototype = prototype;
        }
    }

    /**
     * 无脚本级开关的指令中断检查，保证普通 Lua 循环可协作取消。
     *
     * @author bytex0
     * @since 2026-10-06 14:01:01
     */
    private static final class InterruptDebugLib extends DebugLib {

        /**
         * 每条指令先检查线程中断，再执行标准调试回调。
         *
         * @param programCounter 当前指令位置
         * @param values 当前可变参数
         * @param top 栈顶位置
         */
        @Override
        public void onInstruction(int programCounter, Varargs values, int top) {
            if (Thread.currentThread().isInterrupted()) {
                throw new LuaError("Lua execution interrupted");
            }
            super.onInstruction(programCounter, values, top);
        }
    }
}
