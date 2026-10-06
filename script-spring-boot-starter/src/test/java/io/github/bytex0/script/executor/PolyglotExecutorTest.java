package io.github.bytex0.script.executor;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptExecuteException;
import io.github.bytex0.script.exception.ScriptValidateException;
import io.github.bytex0.script.service.ScriptService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JavaScript、Lua、Python 的真实引擎和类型化服务入口测试。
 *
 * @author bytex0
 * @since 2026-10-06 14:03:07
 */
class PolyglotExecutorTest {

    /**
     * 验证 JavaScript 表达式、函数、独立上下文及脱离引擎的对象结果。
     */
    @Test
    void javascriptRunsExpressionsAndMethodsWithDetachedResults() {
        JavaScriptExecutor executor = new JavaScriptExecutor();
        assertThat(executor.execute("a + 2", Map.of("a", 3))).isEqualTo(5);
        assertThat(executor.executeMethod("function sum(p) { return p.a + p.b; }",
                "sum", Map.of("a", 2, "b", 4))).isEqualTo(6);
        assertThat(executor.execute("({name: 'value', items: [1, 2]})", Map.of()))
                .isEqualTo(Map.of("name", "value", "items", List.of(1, 2)));
        executor.execute("globalThis.previous = 1", Map.of());
        assertThat(executor.execute("typeof previous", Map.of())).isEqualTo("undefined");
        assertThatThrownBy(() -> executor.validate("function {"))
                .isInstanceOf(ScriptValidateException.class);
        assertThatThrownBy(() -> executor.executeMethod("1", "missing", Map.of()))
                .isInstanceOf(ScriptExecuteException.class);
    }

    /**
     * 验证 JavaScript 严格模式配置生效。
     */
    @Test
    void javascriptStrictModeIsEffective() {
        assertThatThrownBy(() -> new JavaScriptExecutor(true).execute("unbound = 1", Map.of()))
                .isInstanceOf(ScriptExecuteException.class);
        assertThat(new JavaScriptExecutor(false).execute("unbound = 1; unbound", Map.of())).isEqualTo(1);
        String hostCall = "Java.type('java.lang.Math').abs(-2)";
        assertThatThrownBy(() -> new JavaScriptExecutor(true).execute(hostCall, Map.of()))
                .isInstanceOf(ScriptExecuteException.class);
        assertThat(new JavaScriptExecutor(true, true).execute(hostCall, Map.of())).isEqualTo(2);
    }

    /**
     * 验证 Lua 字符串协议、指定函数和独立全局环境。
     */
    @Test
    void luaPreservesStringResultsAndSupportsMethods() {
        LuaScriptExecutor executor = new LuaScriptExecutor();
        assertThat(executor.execute("return a + 2", Map.of("a", 3))).isEqualTo("5");
        assertThat(executor.executeMethod("function sum(p) return p.a + p.b end",
                "sum", Map.of("a", 2, "b", 4))).isEqualTo("6");
        executor.execute("previous = 1", Map.of());
        assertThat(executor.execute("return previous", Map.of())).isEqualTo("nil");
        assertThat(executor.execute("return os == nil and io == nil and luajava == nil", Map.of()))
                .isEqualTo("true");
        assertThat(new LuaScriptExecutor(false).execute("return os ~= nil", Map.of())).isEqualTo("true");
        assertThatThrownBy(() -> executor.validate("function"))
                .isInstanceOf(ScriptValidateException.class);
    }

    /**
     * 验证 Jython 实际执行代码对象、指定方法及局部变量隔离。
     */
    @Test
    void pythonExecutesCodeInsteadOfCallingCodeObjects() {
        PythonScriptExecutor executor = new PythonScriptExecutor();
        assertThat(executor.execute("result = a + 2", Map.of("a", 3))).isEqualTo(5);
        assertThat(executor.executeMethod("def sum(p):\n    return p['a'] + p['b']",
                "sum", Map.of("a", 2, "b", 4))).isEqualTo(6);
        executor.execute("previous = 1", Map.of());
        assertThat(executor.execute("result = 'previous' in globals()", Map.of())).isEqualTo(false);
        assertThatThrownBy(() -> executor.validate("def :"))
                .isInstanceOf(ScriptValidateException.class);
        assertThatThrownBy(() -> executor.executeMethod("pass", "missing", Map.of()))
                .isInstanceOf(ScriptExecuteException.class);
    }

    /**
     * 验证类型化服务按 ID 缓存但不忽略源码变化，支持刷新、删除和动态替换。
     */
    @Test
    void typedServiceSupportsCacheManagementAndRegistration() {
        try (ScriptService service = new ScriptService(List.of(new GroovyScriptExecutor()),
                2, 4, 20000, 2)) {
            assertThat(service.execute("id", ScriptType.GROOVY,
                    "def execute(p) { p.a + 1 }", Map.of("a", 2))).isEqualTo(3);
            assertThat(service.execute("id", ScriptType.GROOVY,
                    "def execute(p) { p.a + 2 }", Map.of("a", 2))).isEqualTo(4);
            service.refresh("id", ScriptType.GROOVY, "def execute(p) { 6 }");
            assertThat(service.execute("id", ScriptType.GROOVY, "def execute(p) { 6 }", Map.of())).isEqualTo(6);
            service.remove("id");
            service.validate(ScriptType.GROOVY, "def execute(p) { 7 }");
            service.addExecutor(new LuaScriptExecutor());
            assertThat(service.getSupportedTypes()).containsExactlyInAnyOrder(ScriptType.GROOVY, ScriptType.LUA);
            assertThat(service.executeMethod("id", ScriptType.LUA, "function sum(p) return p.a + 1 end",
                    "sum", Map.of("a", 3))).isEqualTo("4");
        }
    }
}
