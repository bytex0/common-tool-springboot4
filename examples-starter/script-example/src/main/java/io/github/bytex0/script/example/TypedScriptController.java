package io.github.bytex0.script.example;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptValidateException;
import io.github.bytex0.script.service.ScriptService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 类型化脚本示例，只选择服务端固定源码，不接受 HTTP 脚本内容。
 *
 * @author bytex0
 * @since 2026-10-06 14:03:07
 */
@RestController
public class TypedScriptController {

    /**
     * 由 Starter 自动配置提供的类型化服务。
     */
    private final ScriptService service;

    /**
     * 注入自动装配的服务。
     *
     * @param service 类型化脚本服务
     */
    public TypedScriptController(ScriptService service) {
        this.service = service;
    }

    /**
     * 执行指定语言的固定求和方法，覆盖实际引擎与缓存路径。
     *
     * @param type 语言类型，默认 GROOVY
     * @param a 第一个整数
     * @param b 第二个整数
     * @return 统一响应，Lua 结果保留字符串协议
     */
    @GetMapping("/api/script/typed/run")
    public Map<String, Object> run(@RequestParam(defaultValue = "GROOVY") ScriptType type,
                                  @RequestParam(defaultValue = "0") int a,
                                  @RequestParam(defaultValue = "0") int b) {
        String source = switch (type) {
            case GROOVY -> "def sum(p) { p.a + p.b }";
            case JAVASCRIPT -> "function sum(p) { return p.a + p.b; }";
            case LUA -> "function sum(p) return p.a + p.b end";
            case PYTHON -> "import json\ndef sum(p):\n    return json.loads(json.dumps(p['a'] + p['b']))";
            case JAVA -> """
                    import java.util.Map;
                    public class SumScript {
                        public Object sum(Map<String, Object> p) {
                            return (Integer) p.get("a") + (Integer) p.get("b");
                        }
                    }
                    """;
        };
        Object value = service.executeMethod("example-" + type.name(), type, source, "sum", Map.of("a", a, "b", b));
        return Map.of("code", 0, "data", Map.of("value", value));
    }

    /**
     * 使用随机 ID 验证同 ID 源码变化、刷新和删除后的执行结果。
     *
     * @return 各阶段实际计算结果
     */
    @GetMapping("/api/script/typed/cache")
    public Map<String, Object> cache() {
        String id = "example-" + UUID.randomUUID();
        try {
            Object first = service.execute(id, ScriptType.GROOVY, "def execute(p) { 1 }", Map.of());
            Object changed = service.execute(id, ScriptType.GROOVY, "def execute(p) { 2 }", Map.of());
            service.refresh(id, ScriptType.GROOVY, "def execute(p) { 3 }");
            Object refreshed = service.execute(id, ScriptType.GROOVY, "def execute(p) { 3 }", Map.of());
            service.remove(id);
            Object removed = service.execute(id, ScriptType.GROOVY, "def execute(p) { 4 }", Map.of());
            return Map.of("code", 0, "data", Map.of(
                    "first", first, "changed", changed, "refreshed", refreshed, "afterRemoval", removed));
        } finally {
            service.remove(id);
        }
    }

    /**
     * 校验固定正确或错误脚本，不执行客户端提供的源码。
     *
     * @param valid 是否选择语法正确的脚本
     * @return 校验通过响应，语法错误返回 400
     */
    @GetMapping("/api/script/typed/validate")
    public Map<String, Object> validate(@RequestParam(defaultValue = "true") boolean valid) {
        service.validate(ScriptType.GROOVY, valid ? "def execute(p) { 1 }" : "def {");
        return Map.of("code", 0, "data", Map.of("valid", true));
    }

    /**
     * 将参数及语法校验错误映射为 400，不暴露源码或引擎异常。
     *
     * @return 统一错误码
     */
    @ExceptionHandler({IllegalArgumentException.class, ScriptValidateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Integer> invalid() {
        return Map.of("code", 400);
    }
}
