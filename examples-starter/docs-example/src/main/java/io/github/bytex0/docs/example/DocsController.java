package io.github.bytex0.docs.example;

import io.github.bytex0.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 接口文档示例(DocsController)用于验证 OpenAPI 扫描与业务接口不受文档认证影响
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
@RestController
@Tag(name = "sample", description = "Starter测试接口")
@ApiSupport(order = 7)
public class DocsController {

    /**
     * 文档认证不应阻断公开业务接口。
     *
     * @return 连通状态
     */
    @Operation(summary = "示例连通性检查")
    @ApiOperationSupport(order = 3)
    @GetMapping("/api/docs/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
