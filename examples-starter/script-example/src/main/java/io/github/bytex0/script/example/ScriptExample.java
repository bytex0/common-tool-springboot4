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

    private final ScriptService service;

    public ScriptExample(ScriptService service) { this.service = service; }

    public static void main(String[] args) { SpringApplication.run(ScriptExample.class, args); }

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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Integer> invalid() { return Map.of("code", 400); }

    @ExceptionHandler(TimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    Map<String, Integer> timeout() { return Map.of("code", 504); }
}
