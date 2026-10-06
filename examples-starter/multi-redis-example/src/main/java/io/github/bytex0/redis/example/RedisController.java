package io.github.bytex0.redis.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.redis.MultiRedisManager;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis接口(RedisController)命名路由、JSON及TTL测试
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
@RestController
@RequestMapping("/api/redis")
public class RedisController {

    /**
     * 真实客户端管理器
     */
    private final MultiRedisManager manager;

    /**
     * 注入实际命名连接管理器。
     *
     * @param manager 管理器
     */
    public RedisController(MultiRedisManager manager) {
        this.manager = manager;
    }

    /**
     * 查询启用的连接名称。
     *
     * @return 名称集合
     */
    @GetMapping("/names")
    public ApiResponse<Set<String>> names() {
        return ApiResponse.ok(manager.names());
    }

    /**
     * 在指定客户端写入短期测试数据。
     *
     * @param client 客户端名称
     * @param key 测试键
     * @param ttl 有效秒数，范围 1 至 60
     * @param value JSON 请求值
     * @return 成功响应
     */
    @PutMapping("/value")
    public ApiResponse<Void> put(@RequestParam String client, @RequestParam String key,
                                 @RequestParam(defaultValue = "30") long ttl, @RequestBody Object value) {
        Assert.hasText(key, "key不能为空");
        Assert.isTrue(ttl > 0 && ttl <= 60, "示例TTL必须在1至60秒之间");
        manager.get(client).getBucket(key).set(value, Duration.ofSeconds(ttl));
        return ApiResponse.ok();
    }

    /**
     * 读取指定连接的数据，不回退到默认连接。
     *
     * @param client 连接名称
     * @param key 测试键
     * @return 值及存在标志
     */
    @GetMapping("/value")
    public ApiResponse<Map<String, Object>> get(@RequestParam String client, @RequestParam String key) {
        Object value = manager.get(client).getBucket(key).get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("present", value != null);
        return ApiResponse.ok(result);
    }

    /**
     * 删除调用者的测试键。
     *
     * @param client 连接名称
     * @param key 测试键
     * @return 成功响应
     */
    @DeleteMapping("/value")
    public ApiResponse<Void> delete(@RequestParam String client, @RequestParam String key) {
        manager.get(client).getBucket(key).delete();
        return ApiResponse.ok();
    }

    /**
     * 返回不包含连接信息的参数错误。
     *
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("Redis参数不合法", 400));
    }
}
