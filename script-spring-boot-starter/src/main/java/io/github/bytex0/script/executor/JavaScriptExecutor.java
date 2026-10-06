package io.github.bytex0.script.executor;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.exception.ScriptExecuteException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.springframework.util.Assert;

/**
 * GraalJS 执行器，每次执行创建独立 Context，返回脱离上下文的 Java 数据。
 *
 * @author bytex0
 * @since 2026-10-06 14:01:01
 */
public class JavaScriptExecutor implements ScriptExecutor {

    /**
     * 结果最大嵌套深度，避免循环引用导致无限递归。
     */
    private static final int MAX_RESULT_DEPTH = 32;

    /**
     * 单个数组结果最多元素数。
     */
    private static final int MAX_ARRAY_SIZE = 10000;

    /**
     * 是否强制 ECMAScript 严格模式。
     */
    private final boolean strictMode;

    /**
     * 是否显式授予宿主访问权，仅适用于完全受信脚本。
     */
    private final boolean allowHostAccess;

    /**
     * 默认启用严格模式。
     */
    public JavaScriptExecutor() {
        this(true);
    }

    /**
     * 设置 JavaScript 严格模式。
     *
     * @param strictMode true 时强制严格模式
     */
    public JavaScriptExecutor(boolean strictMode) {
        this(strictMode, false);
    }

    /**
     * 配置严格模式和显式宿主访问，恢复原版允许 Java 互操作的可选能力。
     *
     * @param strictMode 是否强制严格模式
     * @param allowHostAccess 是否授予全部宿主访问权，开启前必须确保源码完全受信
     */
    public JavaScriptExecutor(boolean strictMode, boolean allowHostAccess) {
        this.strictMode = strictMode;
        this.allowHostAccess = allowHostAccess;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScriptType getType() {
        return ScriptType.JAVASCRIPT;
    }

    /**
     * 校验并缓存不可变 Source，不缓存绑定在 Context 上的 Value。
     *
     * @param script 非空受信源码
     * @return 本执行器拥有的源码产物
     */
    @Override
    public Object compile(String script) {
        Assert.hasText(script, "Script source is required");
        Source source = Source.newBuilder("js", script, "script.js").buildLiteral();
        try (Context context = newContext()) {
            context.parse(source);
            return new CompiledScript(this, source);
        } catch (RuntimeException exception) {
            throw new ScriptCompileException("JavaScript compilation failed", exception);
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
     * 执行模块并调用指定全局函数，传入可按属性访问的参数对象。
     *
     * @param compiledScript 编译产物
     * @param methodName 全局函数名
     * @param params 参数对象
     * @return 脱离 Context 的 Java 数据
     */
    @Override
    public Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.hasText(methodName, "Method name is required");
        return invoke(compiledScript, methodName, params);
    }

    /**
     * 在独立 Context 中执行并转换结果，避免返回关闭后失效的 Value。
     *
     * @param compiledScript 本执行器编译产物
     * @param methodName 函数名，为空执行脚本体
     * @param params 参数映射
     * @return Java 数据，可为空
     */
    private Object invoke(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.notNull(params, "Script parameters are required");
        Assert.isTrue(compiledScript instanceof CompiledScript, "Invalid JavaScript compiled script");
        CompiledScript compiled = (CompiledScript) compiledScript;
        Assert.isTrue(compiled.owner == this, "Compiled script belongs to another executor");
        try (Context context = newContext()) {
            Value bindings = context.getBindings("js");
            params.forEach(bindings::putMember);
            Value result = context.eval(compiled.source);
            if (methodName != null) {
                Value function = bindings.getMember(methodName);
                Assert.isTrue(function != null && function.canExecute(), "JavaScript function does not exist");
                result = function.execute(ProxyObject.fromMap(new LinkedHashMap<>(params)));
            }
            return detach(result, 0);
        } catch (RuntimeException exception) {
            throw new ScriptExecuteException("JavaScript execution failed", exception);
        }
    }

    /**
     * 默认不授予宿主访问权；显式开启后允许受信脚本进行 Java 和系统互操作。
     *
     * @return 调用者负责关闭的上下文
     */
    private Context newContext() {
        return Context.newBuilder("js")
                .allowAllAccess(allowHostAccess)
                .option("js.strict", Boolean.toString(strictMode))
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    /**
     * 将常见 JavaScript 数据转换为 Java 值，不允许返回可执行对象。
     *
     * @param value 上下文中的结果
     * @param depth 当前嵌套深度
     * @return Java 数据，可为空
     */
    private Object detach(Value value, int depth) {
        Assert.isTrue(depth <= MAX_RESULT_DEPTH, "JavaScript result exceeds maximum depth");
        if (value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.fitsInInt()) {
            return value.asInt();
        }
        if (value.fitsInLong()) {
            return value.asLong();
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.canExecute()) {
            throw new ScriptExecuteException("Executable JavaScript results cannot leave the context");
        }
        if (value.hasArrayElements()) {
            Assert.isTrue(value.getArraySize() <= MAX_ARRAY_SIZE, "JavaScript result array is too large");
            List<Object> result = new ArrayList<>();
            for (long index = 0; index < value.getArraySize(); index++) {
                result.add(detach(value.getArrayElement(index), depth + 1));
            }
            return result;
        }
        if (value.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String name : value.getMemberKeys()) {
                result.put(name, detach(value.getMember(name), depth + 1));
            }
            return result;
        }
        throw new ScriptExecuteException("Unsupported JavaScript result type");
    }

    /**
     * 可跨独立 Context 复用的不可变源码。
     *
     * @author bytex0
     * @since 2026-10-06 14:01:01
     */
    private static final class CompiledScript {

        /**
         * 创建者。
         */
        private final JavaScriptExecutor owner;

        /**
         * GraalJS 源码对象，不包含执行状态。
         */
        private final Source source;

        /**
         * 创建源码产物。
         *
         * @param owner 创建者
         * @param source 已校验源码
         */
        private CompiledScript(JavaScriptExecutor owner, Source source) {
            this.owner = owner;
            this.source = source;
        }
    }
}
