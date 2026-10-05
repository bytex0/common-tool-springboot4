package io.github.bytex0.ratelimiter.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.enums.RedisClientType;
import io.github.bytex0.ratelimiter.exception.RateLimitException;
import io.github.bytex0.ratelimiter.model.FlowRule;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 限流接口(RateController)算法与额度测试
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@RestController
@RequestMapping("/api/rate")
public class RateController {

    /**
     * 实际代理服务
     */
    private final RateService service;

    /**
     * 注入经过 Spring 代理的示例业务服务。
     *
     * @param service 示例业务服务
     */
    public RateController(RateService service) {
        this.service = service;
    }

    /**
     * 使用指定算法和后端申请许可，不直接返回 SDK 对象。
     *
     * @param key 业务键
     * @param type 限流算法
     * @param backend Lua 执行后端
     * @param max 窗口许可数
     * @param window 窗口秒数
     * @param capacity 桶容量
     * @param rate 每秒补充或排出速率
     * @param permits 本次请求许可数
     * @param legacy 是否验证原 Lua 策略参数协议，默认关闭
     * @return 获得许可时返回 allowed=true
     */
    @GetMapping("/acquire")
    public ApiResponse<Map<String, Boolean>> acquire(@RequestParam String key,
            @RequestParam(defaultValue = "REDIS_LUA_FIXED_WINDOW") RateLimiterType type,
            @RequestParam(defaultValue = "REDISSON") RedisClientType backend,
            @RequestParam(defaultValue = "3") int max,
            @RequestParam(defaultValue = "2") int window,
            @RequestParam(defaultValue = "3") int capacity,
            @RequestParam(defaultValue = "1") int rate,
            @RequestParam(defaultValue = "1") int permits,
            @RequestParam(defaultValue = "false") boolean legacy) {
        FlowRule rule = FlowRule.builder().key(key).rateLimiterType(type).redisClientType(backend)
                .maxRequests(max).windowTime(window)
                .bucketCapacity(capacity).tokenRate(rate).permits(permits).build();
        if (legacy) {
            service.legacy(rule);
        } else {
            service.acquire(rule);
        }
        return ApiResponse.ok(Map.of("allowed", true));
    }

    /**
     * 通过 Redisson 注解路径申请许可。
     *
     * @param key 业务键
     * @return 成功响应
     */
    @GetMapping("/annotated")
    public ApiResponse<Void> annotated(@RequestParam String key) {
        service.annotated(key);
        return ApiResponse.ok();
    }

    /**
     * 通过 RedisTemplate 注解路径申请许可，与 Redisson 路径共享额度。
     *
     * @param key 业务键
     * @return 成功响应
     */
    @GetMapping("/annotated-spring")
    public ApiResponse<Void> annotatedSpring(@RequestParam String key) {
        service.annotatedSpring(key);
        return ApiResponse.ok();
    }

    /**
     * 将业务额度不足映射为 HTTP 429，不泄漏内部规则。
     *
     * @return 限流响应
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> rejected() {
        return ResponseEntity.status(429).body(ApiResponse.fail(429));
    }

    /**
     * 将非法规则映射为 HTTP 400。
     *
     * @return 参数错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400));
    }
}
