package io.github.bytex0.sensitive.example;

import io.github.bytex0.sensitive.SensitiveWordService;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动配置敏感词服务的文本处理示例。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
@SpringBootApplication
@RestController
public class SensitiveExample {

    private final SensitiveWordService service;

    public SensitiveExample(SensitiveWordService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        SpringApplication.run(SensitiveExample.class, args);
    }

    @PostMapping("/api/sensitive/process")
    public Map<String, Object> process(@RequestBody String text) {
        return Map.of("code", 0, "data", Map.of("text", service.replace(text), "matches", service.findAll(text, true)));
    }

    @PostMapping("/api/sensitive/reject")
    public Map<String, Object> reject(@RequestBody String text) {
        service.reject(text);
        return Map.of("code", 0, "data", Map.of("accepted", true));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Integer> invalid() {
        return Map.of("code", 400);
    }
}
