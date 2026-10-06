package io.github.bytex0.script.executor;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.exception.ScriptExecuteException;
import io.github.bytex0.script.exception.ScriptValidateException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 编译执行器的兼容方法、生命周期、异常分类与并发状态隔离回归测试。
 *
 * @author bytex0
 * @since 2026-10-06 13:55:16
 */
class CompiledExecutorTest {

    /**
     * 并发测试任务等待上限，单位为秒。
     */
    private static final int WAIT_SECONDS = 20;

    /**
     * 验证 Groovy 默认方法、指定方法与脚本体入口具有独立且确定的语义。
     */
    @Test
    void groovyPreservesMethodAndBodySemantics() {
        GroovyScriptExecutor executor = new GroovyScriptExecutor();
        String source = "def execute(p) { p.a + 1 }; def multiply(p) { p.a * 2 }; return a + 3";
        Object compiled = executor.compile(source);
        try {
            assertThat(executor.executeCompiled(compiled, Map.of("a", 2))).isEqualTo(3);
            assertThat(executor.executeCompiledMethod(compiled, "multiply", Map.of("a", 2))).isEqualTo(4);
            assertThat(executor.executeBody(compiled, Map.of("a", 2))).isEqualTo(5);
            assertThat(executor.getType()).isEqualTo(ScriptType.GROOVY);
            assertThatThrownBy(() -> executor.executeCompiledMethod(compiled, "missing", Map.of()))
                    .isInstanceOf(ScriptExecuteException.class).hasCauseInstanceOf(RuntimeException.class);
        } finally {
            executor.release(compiled);
        }
        assertThatThrownBy(() -> executor.executeCompiled(compiled, Map.of()))
                .isInstanceOf(IllegalStateException.class);
        executor.release(compiled);
    }

    /**
     * 验证同一 Groovy 编译产物可并发执行，不共享 Binding 或脚本实例。
     *
     * @throws Exception 并发任务失败或等待超时
     */
    @Test
    void groovyCompiledBindingsAreIsolated() throws Exception {
        GroovyScriptExecutor executor = new GroovyScriptExecutor();
        Object compiled = executor.compile("def execute(p) { binding.setVariable('a', p.a); return a }");
        try (ThreadPoolExecutor clients = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2), new ThreadPoolExecutor.AbortPolicy())) {
            var first = clients.submit(() -> executor.executeCompiled(compiled, Map.of("a", 10)));
            var second = clients.submit(() -> executor.executeCompiled(compiled, Map.of("a", 20)));
            assertThat(first.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(10);
            assertThat(second.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(20);
        } finally {
            executor.release(compiled);
        }
    }

    /**
     * 验证 Java 使用语法树识别包和类名，默认及指定方法均可执行。
     */
    @Test
    void javaCompilesPackagedClassAndInvokesMethods() {
        JavaExecutor executor = new JavaExecutor();
        String source = """
                package sample.scripts;
                import java.util.Map;
                /* public class MisleadingName */
                public final class Calculation{
                    private static int count;
                    public Object execute(Map<String, Object> p) {
                        return (Integer) p.get("a") + ++count;
                    }
                    public Object twice(Map<String, Object> p) {
                        return (Integer) p.get("a") * 2;
                    }
                }
                """;
        Object compiled = executor.compile(source);
        try {
            assertThat(executor.executeCompiled(compiled, Map.of("a", 2))).isEqualTo(3);
            assertThat(executor.executeCompiled(compiled, Map.of("a", 2))).isEqualTo(3);
            assertThat(executor.executeCompiledMethod(compiled, "twice", Map.of("a", 3))).isEqualTo(6);
            assertThat(executor.getType()).isEqualTo(ScriptType.JAVA);
            assertThatThrownBy(() -> executor.executeCompiledMethod(compiled, "missing", Map.of()))
                    .isInstanceOf(ScriptExecuteException.class).hasCauseInstanceOf(NoSuchMethodException.class);
        } finally {
            executor.release(compiled);
        }
        executor.release(compiled);
        assertThatThrownBy(() -> executor.executeCompiled(compiled, Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 验证同名不同源码不覆盖对方的编译产物。
     */
    @Test
    void javaSameNameRevisionsRemainIndependent() {
        JavaExecutor executor = new JavaExecutor();
        String source = "import java.util.Map; public class Revision { public Object execute(Map p) { return %d; } }";
        Object first = executor.compile(source.formatted(1));
        Object second = executor.compile(source.formatted(2));
        try {
            assertThat(executor.executeCompiled(first, Map.of())).isEqualTo(1);
            assertThat(executor.executeCompiled(second, Map.of())).isEqualTo(2);
            assertThat(executor.executeCompiled(first, Map.of())).isEqualTo(1);
        } finally {
            executor.release(first);
            executor.release(second);
        }
    }

    /**
     * 验证编译、校验失败分类，以及拒绝使用其他执行器的编译产物。
     */
    @Test
    void compilationValidationAndOwnershipFailuresAreExplicit() {
        for (ScriptExecutor executor : List.of(new GroovyScriptExecutor(), new JavaExecutor())) {
            assertThatThrownBy(() -> executor.compile("public class {"))
                    .isInstanceOf(ScriptCompileException.class);
            assertThatThrownBy(() -> executor.validate("public class {"))
                    .isInstanceOf(ScriptValidateException.class).hasCauseInstanceOf(ScriptCompileException.class);
            assertThatThrownBy(() -> executor.executeCompiled(new Object(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        GroovyScriptExecutor owner = new GroovyScriptExecutor();
        Object compiled = owner.compile("def execute(p) { 1 }");
        try {
            assertThatThrownBy(() -> new GroovyScriptExecutor().executeCompiled(compiled, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            owner.release(compiled);
        }
    }

    /**
     * 验证清理异常作为 suppressed 保留，不覆盖脚本原始执行异常。
     */
    @Test
    void cleanupFailureDoesNotHideExecutionFailure() {
        FailingExecutor executor = new FailingExecutor();
        assertThatThrownBy(() -> executor.execute("", Map.of()))
                .isSameAs(executor.failure)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .hasSize(1).allMatch(ScriptCompileException.class::isInstance));
    }

    /**
     * 同时产生执行及清理失败的测试替身。
     *
     * @author bytex0
     * @since 2026-10-06 13:55:16
     */
    private static final class FailingExecutor implements ScriptExecutor {

        /**
         * 需要完整保留的执行异常实例。
         */
        private final ScriptExecuteException failure = new ScriptExecuteException("execution");

        /**
         * {@inheritDoc}
         */
        @Override
        public ScriptType getType() {
            return ScriptType.GROOVY;
        }

        /**
         * 返回关闭时固定失败的资源。
         *
         * @param script 测试中忽略
         * @return 关闭失败的资源
         */
        @Override
        public Object compile(String script) {
            return (AutoCloseable) () -> {
                throw new IllegalStateException("cleanup");
            };
        }

        /**
         * 抛出固定执行异常。
         *
         * @param compiledScript 测试资源
         * @param params 测试参数
         * @return 不返回
         */
        @Override
        public Object executeCompiled(Object compiledScript, Map<String, Object> params) {
            throw failure;
        }

        /**
         * 抛出固定执行异常。
         *
         * @param compiledScript 测试资源
         * @param methodName 测试方法名
         * @param params 测试参数
         * @return 不返回
         */
        @Override
        public Object executeCompiledMethod(Object compiledScript, String methodName, Map<String, Object> params) {
            throw failure;
        }
    }
}
