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

    /**
     * 注入真实消息服务及管理器。
     *
     * @param service 查询服务
     * @param manager 内存管理器
     */
    public I18nController(I18nService service, I18nManager manager) {
        this.service = service;
        this.manager = manager;
    }

    /**
     * 使用当前请求协商语言查询，显式数组避免原字符串重载歧义。
     *
     * @param code 消息编码
     * @param name 格式化参数
     * @return 实际消息
     */
    @GetMapping("/message")
    public ApiResponse<Map<String, String>> message(@RequestParam(defaultValue = "hello") String code,
                                                    @RequestParam(defaultValue = "World") String name) {
        return ApiResponse.ok(Map.of("message", service.getMessage(code, new Object[]{name})));
    }

    /**
     * 验证原语言标签及默认文本重载。
     *
     * @param code 消息编码
     * @param language 语言标签，空字符串使用当前请求语言
     * @param fallback 默认文本
     * @param name 格式化参数
     * @return 消息或默认文本
     */
    @GetMapping("/default")
    public ApiResponse<Map<String, String>> defaultMessage(@RequestParam String code,
            @RequestParam(defaultValue = "") String language,
            @RequestParam(defaultValue = "Default {0}") String fallback,
            @RequestParam(defaultValue = "World") String name) {
        return ApiResponse.ok(Map.of("message", service.getMessageByLocale(code, language, fallback, name)));
    }

    /**
     * 新增或替换测试消息。
     *
     * @param language 目标语言
     * @param code 消息编码
     * @param text 消息文本
     * @return 成功响应
     */
    @PutMapping("/message")
    public ApiResponse<Void> put(@RequestParam String language, @RequestParam String code, @RequestParam String text) {
        manager.addMessage(language, code, text);
        return ApiResponse.ok();
    }

    /**
     * 删除一条消息。
     *
     * @param language 目标语言
     * @param code 消息编码
     * @return 成功响应
     */
    @DeleteMapping("/message")
    public ApiResponse<Void> remove(@RequestParam String language, @RequestParam String code) {
        manager.removeMessage(language, code);
        return ApiResponse.ok();
    }

    /**
     * 明确删除一种或所有语言的数据，仅供独立示例验证。
     *
     * @param language 目标语言，未指定时清空全部
     * @return 成功响应
     */
    @DeleteMapping("/messages")
    public ApiResponse<Void> clear(@RequestParam(required = false) String language) {
        if (language == null) {
            manager.clear();
        } else {
            manager.clear(language);
        }
        return ApiResponse.ok();
    }

    /**
     * 刷新派生缓存，不删除内存数据源。
     *
     * @return 成功响应
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() {
        manager.refresh();
        return ApiResponse.ok();
    }

    /**
     * 返回无效语言参数的错误响应。
     *
     * @return HTTP 400 响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("语言参数不合法", 400));
    }
}
