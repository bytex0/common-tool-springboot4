package io.github.bytex0.ratelimiter.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
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

    public RateController(RateService service) { this.service = service; }

    @GetMapping("/acquire")
    public ApiResponse<Map<String, Boolean>> acquire(@RequestParam String key,
            @RequestParam(defaultValue = "REDIS_LUA_FIXED_WINDOW") RateLimiterType type,
            @RequestParam(defaultValue = "3") int max,
            @RequestParam(defaultValue = "2") int window,
            @RequestParam(defaultValue = "3") int capacity,
            @RequestParam(defaultValue = "1") int rate,
            @RequestParam(defaultValue = "1") int permits) {
        service.acquire(FlowRule.builder().key(key).rateLimiterType(type).maxRequests(max).windowTime(window)
                .bucketCapacity(capacity).tokenRate(rate).permits(permits).build());
        return ApiResponse.ok(Map.of("allowed", true));
    }

    @GetMapping("/annotated")
    public ApiResponse<Void> annotated(@RequestParam String key) {
        service.annotated(key);
        return ApiResponse.ok();
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> rejected() { return ResponseEntity.status(429).body(ApiResponse.fail(429)); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() { return ResponseEntity.badRequest().body(ApiResponse.fail(400)); }
}
