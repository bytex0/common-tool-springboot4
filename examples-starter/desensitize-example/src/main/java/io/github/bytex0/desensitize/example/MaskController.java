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

    @GetMapping("/api/desensitize/profile")
    public ApiResponse<MaskProfile> profile() { return ApiResponse.ok(new MaskProfile()); }

    @GetMapping("/api/desensitize/list")
    public ApiResponse<List<MaskProfile>> list() { return ApiResponse.ok(List.of(new MaskProfile(), new MaskProfile())); }
}
