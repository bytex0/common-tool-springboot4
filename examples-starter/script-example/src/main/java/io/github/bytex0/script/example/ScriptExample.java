package io.github.bytex0.script.example;

import io.github.bytex0.script.ScriptService;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只执行固定目录中的受信脚本，HTTP 参数不能提供源代码。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
@SpringBootApplication
@RestController
public class ScriptExample {

    /**
     * 自动装配的根包脚本体服务。
     */
    private final ScriptService service;

    /**
     * 注入脚本服务。
     *
     * @param service 自动配置提供的服务
     */
    public ScriptExample(ScriptService service) {
        this.service = service;
    }

    /**
     * 启动脚本示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ScriptExample.class, args);
    }

    /**
     * 执行服务端固定脚本，保留已有脚本体接口。
     *
     * @param name 固定脚本名称，默认 sum
     * @param a 第一个整数，默认 0
     * @param b 第二个整数，默认 0
     * @return 统一结果响应
     * @throws Exception 编译、执行或超时失败
     */
    @GetMapping("/api/script/run")
    Map<String, Object> run(@RequestParam(defaultValue = "sum") String name,
                           @RequestParam(defaultValue = "0") int a, @RequestParam(defaultValue = "0") int b) throws Exception {
        String source = switch (name) {
            case "sum" -> "return a + b";
            case "product" -> "return a * b";
            case "timeout" -> "while (true) { }";
            default -> throw new IllegalArgumentException("Unknown script");
        };
        return Map.of("code", 0, "data", Map.of("value", service.execute("groovy", source, Map.of("a", a, "b", b))));
    }

    /**
     * 返回无效脚本名称错误，不输出源代码。
     *
     * @return 错误码
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Integer> invalid() {
        return Map.of("code", 400);
    }

    /**
     * 返回脚本等待超时错误。
     *
     * @return 错误码
     */
    @ExceptionHandler(TimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    Map<String, Integer> timeout() {
        return Map.of("code", 504);
    }
}
