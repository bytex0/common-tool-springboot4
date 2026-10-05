package io.github.bytex0.i18n.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.i18n.provider.I18nManager;
import io.github.bytex0.i18n.service.I18nService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 国际化接口(I18nController)语言协商和动态消息测试
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
@RestController
@RequestMapping("/api/i18n")
public class I18nController {

    /**
     * 消息查询服务
     */
    private final I18nService service;

    /**
     * 动态消息管理
     */
    private final I18nManager manager;

    public I18nController(I18nService service, I18nManager manager) {
        this.service = service;
        this.manager = manager;
    }

    @GetMapping("/message")
    public ApiResponse<Map<String, String>> message(@RequestParam(defaultValue = "hello") String code,
                                                    @RequestParam(defaultValue = "World") String name) {
        return ApiResponse.ok(Map.of("message", service.getMessage(code, new Object[]{name})));
    }

    @PutMapping("/message")
    public ApiResponse<Void> put(@RequestParam String language, @RequestParam String code, @RequestParam String text) {
        manager.addMessage(language, code, text);
        return ApiResponse.ok();
    }

    @DeleteMapping("/message")
    public ApiResponse<Void> remove(@RequestParam String language, @RequestParam String code) {
        manager.removeMessage(language, code);
        return ApiResponse.ok();
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() {
        manager.refresh();
        return ApiResponse.ok();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("语言参数不合法", 400));
    }
}
