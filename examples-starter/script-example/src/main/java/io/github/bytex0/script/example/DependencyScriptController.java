package io.github.bytex0.script.example;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.service.ScriptService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 显式启用的 Ivy 依赖解析示例，只能请求固定测试依赖，仓库由自动化脚本在 JVM 启动前配置。
 *
 * @author bytex0
 * @since 2026-10-25 11:10:02
 */
@RestController
@ConditionalOnProperty(prefix = "script.example", name = "ivy-enabled", havingValue = "true")
public class DependencyScriptController {

    /**
     * 服务端固定受信源码，依赖仅包含测试资源；不接受客户端提供的源码或依赖坐标。
     */
    private static final String SOURCE = """
            @Grab('io.github.bytex0.fixture:%s:1.0')
            import java.nio.charset.StandardCharsets
            def execute(p) {
                def stream = getClass().classLoader.getResourceAsStream('bytex0-fixture/value.txt')
                if (stream == null) {
                    throw new IllegalStateException('Fixture resource not resolved')
                }
                stream.withCloseable {
                    new String(it.readNBytes(1024), StandardCharsets.UTF_8)
                }
            }
            """;

    /**
     * Starter 自动配置提供的类型化服务。
     */
    private final ScriptService service;

    /**
     * 注入真实 Starter 服务，不在示例内替代解析或编译实现。
     *
     * @param service 类型化服务
     */
    public DependencyScriptController(ScriptService service) {
        this.service = service;
    }

    /**
     * 解析固定存在或不存在的测试依赖，返回从依赖 JAR 中实际读取的内容。
     *
     * @param available true 选择已准备的测试依赖，false 验证缺失依赖错误
     * @return 统一业务响应
     */
    @GetMapping("/api/script/typed/dependency")
    public Map<String, Object> dependency(@RequestParam(defaultValue = "true") boolean available) {
        String artifact = available ? "script-payload" : "script-missing";
        Object value = service.execute("ivy-fixture-" + artifact, ScriptType.GROOVY,
                SOURCE.formatted(artifact), Map.of());
        return Map.of("code", 0, "data", Map.of("value", value));
    }

    /**
     * 缺失依赖与其他编译失败统一映射为 400，不暴露源码、仓库路径或内部异常。
     *
     * @return 统一错误码
     */
    @ExceptionHandler(ScriptCompileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Integer> invalidDependency() {
        return Map.of("code", 400);
    }
}
