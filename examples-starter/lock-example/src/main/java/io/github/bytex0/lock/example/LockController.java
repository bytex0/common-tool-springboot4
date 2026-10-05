package io.github.bytex0.lock.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.exception.LockException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 锁接口(LockController)并发、失败释放及注解测试
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
@RestController
@RequestMapping("/api/lock")
public class LockController {

    /**
     * 实际代理后的业务服务
     */
    private final LockService service;

    public LockController(LockService service) { this.service = service; }

    @GetMapping("/run")
    public ApiResponse<Map<String, Long>> run(@RequestParam String key,
                                             @RequestParam(defaultValue = "REDISSON_LOCK") LockType type,
                                             @RequestParam(defaultValue = "1") int permits,
                                             @RequestParam(defaultValue = "3000") long wait,
                                             @RequestParam(defaultValue = "50") long delay,
                                             @RequestParam(defaultValue = "false") boolean fail) throws Throwable {
        return ApiResponse.ok(Map.of("active", service.run(key, type, permits, wait, delay, fail)));
    }

    @GetMapping("/annotated")
    public ApiResponse<Map<String, Long>> annotated(@RequestParam String key) throws InterruptedException {
        return ApiResponse.ok(Map.of("active", service.annotated(key)));
    }

    @GetMapping("/active")
    public ApiResponse<Map<String, Long>> active(@RequestParam String key) {
        return ApiResponse.ok(Map.of("active", service.active(key)));
    }

    @ExceptionHandler(LockException.class)
    public ResponseEntity<ApiResponse<Void>> rejected() {
        return ResponseEntity.status(423).body(ApiResponse.failOfMessage("未获取锁或锁已丢失", 423));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> failed() {
        return ResponseEntity.internalServerError().body(ApiResponse.fail(500));
    }
}
