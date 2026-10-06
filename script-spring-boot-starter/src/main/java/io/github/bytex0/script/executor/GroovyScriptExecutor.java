package io.github.bytex0.script.executor;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.exception.ScriptExecuteException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.util.Assert;

/**
 * Groovy 兼容执行器，仅缓存编译类型，每次调用创建独立 Script 和 Binding。
 *
 * @author bytex0
 * @since 2026-10-06 13:18:57
 */
public class GroovyScriptExecutor implements ScriptExecutor {

    /**
     * 默认方法名称，保持原版 execute(Map) 语义。
     */
    private static final String DEFAULT_METHOD = "execute";

    /**
     * {@inheritDoc}
     */
    @Override
    public ScriptType getType() {
        return ScriptType.GROOVY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object compile(String script) {
        Assert.hasText(script, "Script source is required");
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setSourceEncoding("UTF-8");
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader(), configuration);
        try {
            Class<? extends Script> type = loader.parseClass(script).asSubclass(Script.class);
            return new CompiledScript(this, loader, type);
        } catch (RuntimeException exception) {
            try {
                loader.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw new ScriptCompileException("Groovy compilation failed", exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object executeCompiled(Object compiledScript, Map<String, Object> params) {
        return executeCompiledMethod(compiledScript, DEFAULT_METHOD, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params) {
        Assert.hasText(methodName, "Method name is required");
        Script instance = newInstance(compiledScript, params);
        try {
            return instance.invokeMethod(methodName, new Object[]{new HashMap<>(params)});
        } catch (RuntimeException exception) {
            throw new ScriptExecuteException("Groovy method execution failed", exception);
        }
    }

    /**
     * 执行脚本体，供 Boot 4 已有根包入口使用，不改变兼容接口默认方法。
     *
     * @param compiledScript 本执行器创建的未关闭产物
     * @param params 非空绑定参数
     * @return 脚本体结果，可为空
     */
    public Object executeBody(Object compiledScript, Map<String, Object> params) {
        try {
            return newInstance(compiledScript, params).run();
        } catch (RuntimeException exception) {
            throw new ScriptExecuteException("Groovy body execution failed", exception);
        }
    }

    /**
     * 校验编译产物归属和关闭状态，创建独立脚本实例。
     *
     * @param compiledScript 编译产物
     * @param params 非空参数
     * @return 独立实例
     */
    private Script newInstance(Object compiledScript, Map<String, Object> params) {
        Assert.notNull(params, "Script parameters are required");
        Assert.isTrue(compiledScript instanceof CompiledScript, "Invalid Groovy compiled script");
        CompiledScript compiled = (CompiledScript) compiledScript;
        Assert.isTrue(compiled.owner == this, "Compiled script belongs to another executor");
        Assert.state(!compiled.closed.get(), "Compiled script is closed");
        return InvokerHelper.createScript(compiled.type, new Binding(new HashMap<>(params)));
    }

    /**
     * 编译类型与类加载器的资源句柄；关闭前调用方必须结束全部执行。
     *
     * @author bytex0
     * @since 2026-10-06 13:18:57
     */
    private static final class CompiledScript implements AutoCloseable {

        /**
         * 创建该产物的执行器实例。
         */
        private final GroovyScriptExecutor owner;

        /**
         * 本次编译独享的类加载器。
         */
        private final GroovyClassLoader loader;

        /**
         * 可重复实例化的脚本类型。
         */
        private final Class<? extends Script> type;

        /**
         * 幂等关闭标记，不用于替代调用方的生命周期协调。
         */
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 接管成功编译后的资源。
         *
         * @param owner 创建者
         * @param loader 独立类加载器
         * @param type 编译类型
         */
        private CompiledScript(GroovyScriptExecutor owner, GroovyClassLoader loader, Class<? extends Script> type) {
            this.owner = owner;
            this.loader = loader;
            this.type = type;
        }

        /**
         * 幂等释放类缓存和类加载器，不允许与执行并发调用。
         *
         * @throws IOException 类加载器关闭失败
         */
        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                try {
                    InvokerHelper.removeClass(type);
                } finally {
                    loader.close();
                }
            }
        }
    }
}
