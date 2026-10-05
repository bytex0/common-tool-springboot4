package io.github.bytex0.idempotent.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.idempotent.exception.IdempotentException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 幂等接口(IdempotentController)重复、长任务及失败重试测试
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@RestController
@RequestMapping("/api/idempotent")
public class IdempotentController {

    /**
     * 实际被代理的服务
     */
    private final IdempotentService service;

    public IdempotentController(IdempotentService service) { this.service = service; }

    @PostMapping("/run")
    public ApiResponse<Map<String, Long>> run(@RequestParam String key,
                                             @RequestParam(defaultValue = "50") long delay,
                                             @RequestParam(defaultValue = "false") boolean fail) throws InterruptedException {
        return ApiResponse.ok(Map.of("count", service.run(key, delay, fail)));
    }

    @GetMapping("/state")
    public ApiResponse<Map<String, Long>> state(@RequestParam String key) {
        return ApiResponse.ok(Map.of("active", service.active(key), "count", service.count(key)));
    }

    @ExceptionHandler(IdempotentException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate() {
        return ResponseEntity.status(409).body(ApiResponse.failOfMessage("重复请求或处理锁丢失", 409));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> failure() { return ResponseEntity.internalServerError().body(ApiResponse.fail(500)); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() { return ResponseEntity.badRequest().body(ApiResponse.fail(400)); }
}
