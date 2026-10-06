package io.github.bytex0.desensitize.example;

import io.github.bytex0.common.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 脱敏接口(MaskController)验证单对象及嵌套集合的出站序列化
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@RestController
public class MaskController {

    /**
     * 输出单个合成模型，由 Starter 的自动 Jackson 模块处理。
     *
     * @return 合成模型响应
     */
    @GetMapping("/api/desensitize/profile")
    public ApiResponse<MaskProfile> profile() {
        return ApiResponse.ok(new MaskProfile());
    }

    /**
     * 输出嵌套集合，验证模块在集合元素中同样生效。
     *
     * @return 模型列表
     */
    @GetMapping("/api/desensitize/list")
    public ApiResponse<List<MaskProfile>> list() {
        return ApiResponse.ok(List.of(new MaskProfile(), new MaskProfile()));
    }
}
