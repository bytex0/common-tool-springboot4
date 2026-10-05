package io.github.bytex0.example.controller;

import io.github.bytex0.common.model.ApiResponse;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 基础工具示例(DemoController)接口入口
 *
 * @author linshiqiang
 * @since 2026-10-05 14:26:50
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    /**
     * 当前应用运行环境
     */
    private final Environment environment;

    public DemoController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of(
                "application", environment.getProperty("spring.application.name", "application"),
                "status", "UP"));
    }
}
