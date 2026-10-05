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
 * @author bytex0
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

    /**
     * 注入实际缓存和注册工厂。
     *
     * @param cache 示例缓存
     * @param factory 当前上下文工厂
     */
    public CacheController(DemoCache cache, LocalCaffeineCacheFactory factory) {
        this.cache = cache;
        this.factory = factory;
    }

    /**
     * 写入缓存条目。
     *
     * @param key 非空业务键
     * @param value 缓存内容
     * @return 成功响应
     */
    @PutMapping("/entry")
    public ApiResponse<Void> put(@RequestParam String key, @RequestParam String value) {
        Assert.hasText(key, "key 不能为空");
        cache.put(key, value);
        return ApiResponse.ok();
    }

    /**
     * 获取条目，不触发业务加载。
     *
     * @param key 非空业务键
     * @return 是否存在及对应内容
     */
    @GetMapping("/entry")
    public ApiResponse<Map<String, Object>> get(@RequestParam String key) {
        Assert.hasText(key, "key 不能为空");
        return value(cache.get(key));
    }

    /**
     * 通过缓存原子加载缺失内容。
     *
     * @param key 非空业务键
     * @param value 缺失时加载的内容
     * @return 实际缓存值及加载次数
     */
    @GetMapping("/load")
    public ApiResponse<Map<String, Object>> load(@RequestParam String key, @RequestParam String value) {
        Assert.hasText(key, "key 不能为空");
        return value(cache.load(key, value));
    }

    /**
     * 删除条目。
     *
     * @param key 非空业务键
     * @return 成功响应
     */
    @DeleteMapping("/entry")
    public ApiResponse<Void> remove(@RequestParam String key) {
        Assert.hasText(key, "key 不能为空");
        cache.remove(key);
        return ApiResponse.ok();
    }

    /**
     * 清空全部示例缓存。
     *
     * @return 成功响应
     */
    @DeleteMapping("/all")
    public ApiResponse<Void> clear() {
        factory.clearAllCaches();
        return ApiResponse.ok();
    }

    /**
     * 返回按 Bean 名称组织的全部缓存统计。
     *
     * @return 命名统计视图
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Map<String, Object>>> stats() {
        cache.cleanUp();
        return ApiResponse.ok(factory.getCacheStats());
    }

    /**
     * 返回与原工厂类简单名键形状一致的统计视图。
     *
     * @return 原形状的统计数据，不直接序列化缓存实例
     */
    @GetMapping("/type-stats")
    public ApiResponse<Map<String, Map<String, Object>>> typeStats() {
        return ApiResponse.ok(factory.getCacheStatsByClassName());
    }

    /**
     * 将非法键映射为明确的 HTTP 参数错误。
     *
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalidArgument() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("请求参数不合法", 400));
    }

    /**
     * 构造允许缓存值为空的响应体。
     *
     * @param value 实际缓存值
     * @return 序列化友好的响应
     */
    private ApiResponse<Map<String, Object>> value(String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("present", value != null);
        result.put("value", value);
        result.put("loadCount", cache.getLoadCount());
        return ApiResponse.ok(result);
    }
}
