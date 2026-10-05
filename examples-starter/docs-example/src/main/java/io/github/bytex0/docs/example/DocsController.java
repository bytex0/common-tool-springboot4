package io.github.bytex0.docs.example;

import io.github.bytex0.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 接口文档示例(DocsController)用于验证 OpenAPI 扫描与业务接口不受文档认证影响
 *
 * @author linshiqiang
 * @since 2026-10-05 15:29:19
 */
@RestController
public class DocsController {

    @Operation(summary = "示例连通性检查")
    @GetMapping("/api/docs/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
