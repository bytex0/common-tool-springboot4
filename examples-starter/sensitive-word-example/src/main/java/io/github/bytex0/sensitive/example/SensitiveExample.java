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

    /**
     * 根包兼容门面。
     */
    private final SensitiveWordService service;

    /**
     * 注入真实自动配置服务。
     *
     * @param service 组件
     */
    public SensitiveExample(SensitiveWordService service) {
        this.service = service;
    }

    /**
     * 启动仅供本地联调的应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SensitiveExample.class, args);
    }

    /**
     * 保留根包原文结果与码点替换。
     *
     * @param text 原文
     * @return 处理与匹配结果
     */
    @PostMapping("/api/sensitive/process")
    public Map<String, Object> process(@RequestBody String text) {
        return Map.of("code", 0, "data", Map.of("text", service.replace(text), "matches", service.findAll(text, true)));
    }

    /**
     * 保留根包拒绝接口。
     *
     * @param text 原文
     * @return 是否通过
     */
    @PostMapping("/api/sensitive/reject")
    public Map<String, Object> reject(@RequestBody String text) {
        service.reject(text);
        return Map.of("code", 0, "data", Map.of("accepted", true));
    }

    /**
     * 转换为安全的请求错误。
     *
     * @return 错误码
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Integer> invalid() {
        return Map.of("code", 400);
    }
}
