package io.github.bytex0.cache.example;

import io.github.bytex0.cache.factory.LocalCaffeineCacheFactory;
import io.github.bytex0.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地缓存(CacheController)真实 Starter 操作测试接口
 *
 * @author linshiqiang
 * @since 2026-10-05 15:14:19
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    /**
     * 当前应用的示例缓存
     */
    private final DemoCache cache;

    /**
     * Starter 自动装配的缓存工厂
     */
    private final LocalCaffeineCacheFactory factory;

    public CacheController(DemoCache cache, LocalCaffeineCacheFactory factory) {
        this.cache = cache;
        this.factory = factory;
    }

    @PutMapping("/entry")
    public ApiResponse<Void> put(@RequestParam String key, @RequestParam String value) {
        Assert.hasText(key, "key 不能为空");
        cache.put(key, value);
        return ApiResponse.ok();
    }

    @GetMapping("/entry")
    public ApiResponse<Map<String, Object>> get(@RequestParam String key) {
        Assert.hasText(key, "key 不能为空");
        return value(cache.get(key));
    }

    @GetMapping("/load")
    public ApiResponse<Map<String, Object>> load(@RequestParam String key, @RequestParam String value) {
        Assert.hasText(key, "key 不能为空");
        return value(cache.load(key, value));
    }

    @DeleteMapping("/entry")
    public ApiResponse<Void> remove(@RequestParam String key) {
        Assert.hasText(key, "key 不能为空");
        cache.remove(key);
        return ApiResponse.ok();
    }

    @DeleteMapping("/all")
    public ApiResponse<Void> clear() {
        factory.clearAllCaches();
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Map<String, Object>>> stats() {
        cache.cleanUp();
        return ApiResponse.ok(factory.getCacheStats());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalidArgument() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("请求参数不合法", 400));
    }

    private ApiResponse<Map<String, Object>> value(String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("present", value != null);
        result.put("value", value);
        result.put("loadCount", cache.getLoadCount());
        return ApiResponse.ok(result);
    }
}
