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

    /**
     * 注入经过 Spring 代理的业务服务。
     *
     * @param service 示例服务
     */
    public IdempotentController(IdempotentService service) {
        this.service = service;
    }

    /**
     * 执行有副作用的同步业务。
     *
     * @param key 业务标识
     * @param delay 延时毫秒数
     * @param fail 是否模拟失败
     * @return 累计成功次数
     * @throws InterruptedException 执行被中断
     */
    @PostMapping("/run")
    public ApiResponse<Map<String, Long>> run(@RequestParam String key,
                                             @RequestParam(defaultValue = "50") long delay,
                                             @RequestParam(defaultValue = "false") boolean fail) throws InterruptedException {
        return ApiResponse.ok(Map.of("count", service.run(key, delay, fail)));
    }

    /**
     * 通过原独立检查接口预占窗口，不执行额外业务。
     *
     * @param key 与 run 共享命名空间的标识
     * @return 预占结果
     */
    @PostMapping("/reserve")
    public ApiResponse<Map<String, Boolean>> reserve(@RequestParam String key) {
        service.reserve(key);
        return ApiResponse.ok(Map.of("reserved", true));
    }

    /**
     * 查询跨实例共享的业务状态。
     *
     * @param key 业务标识
     * @return 当前活动数与成功次数
     */
    @GetMapping("/state")
    public ApiResponse<Map<String, Long>> state(@RequestParam String key) {
        return ApiResponse.ok(Map.of("active", service.active(key), "count", service.count(key)));
    }

    /**
     * 重复请求统一映射为 HTTP 409。
     *
     * @return 不泄露内部业务键的冲突响应
     */
    @ExceptionHandler(IdempotentException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate() {
        return ResponseEntity.status(409).body(ApiResponse.failOfMessage("重复请求或处理锁丢失", 409));
    }

    /**
     * 模拟业务失败或存储状态错误返回 HTTP 500。
     *
     * @return 失败响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> failure() {
        return ResponseEntity.internalServerError().body(ApiResponse.fail(500));
    }

    /**
     * 参数错误返回 HTTP 400。
     *
     * @return 参数错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400));
    }
}
