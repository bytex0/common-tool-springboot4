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
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@RestController
@RequestMapping("/api/lock")
public class LockController {

    /**
     * 实际代理后的业务服务
     */
    private final LockService service;

    /**
     * 注入经过 Spring 代理的服务。
     *
     * @param service 业务服务
     */
    public LockController(LockService service) {
        this.service = service;
    }

    /**
     * 验证指定策略的模板调用。
     *
     * @param key 业务键
     * @param type 策略类型
     * @param permits 总额度
     * @param wait 等待毫秒数
     * @param delay 延时毫秒数
     * @param fail 是否模拟失败
     * @return 活动业务数
     * @throws Throwable 业务或锁错误
     */
    @GetMapping("/run")
    public ApiResponse<Map<String, Long>> run(@RequestParam String key,
                                             @RequestParam(defaultValue = "REDISSON_LOCK") LockType type,
                                             @RequestParam(defaultValue = "1") int permits,
                                             @RequestParam(defaultValue = "3000") long wait,
                                             @RequestParam(defaultValue = "50") long delay,
                                             @RequestParam(defaultValue = "false") boolean fail) throws Throwable {
        return ApiResponse.ok(Map.of("active", service.run(key, type, permits, wait, delay, fail)));
    }

    /**
     * 验证注解调用。
     *
     * @param key 业务键
     * @return 活动业务数
     * @throws InterruptedException 业务被中断
     */
    @GetMapping("/annotated")
    public ApiResponse<Map<String, Long>> annotated(@RequestParam String key) throws InterruptedException {
        return ApiResponse.ok(Map.of("active", service.annotated(key)));
    }

    /**
     * 验证原策略工厂的独立获取与释放。
     *
     * @param key 业务键
     * @param type 锁策略
     * @return 活动业务数
     * @throws InterruptedException 业务被中断
     */
    @GetMapping("/factory")
    public ApiResponse<Map<String, Long>> factory(@RequestParam String key,
                                                 @RequestParam(defaultValue = "REDISSON_LOCK") LockType type)
            throws InterruptedException {
        return ApiResponse.ok(Map.of("active", service.factory(key, type)));
    }

    /**
     * 验证完整规则表达式与后端选择。
     *
     * @param key 业务键
     * @param permits 动态额度
     * @return 活动业务数
     * @throws InterruptedException 业务被中断
     */
    @GetMapping("/dynamic")
    public ApiResponse<Map<String, Long>> dynamic(@RequestParam String key, @RequestParam(defaultValue = "1") int permits)
            throws InterruptedException {
        return ApiResponse.ok(Map.of("active", service.dynamic(key, permits)));
    }

    /**
     * 验证独立额度表达式。
     *
     * @param key 业务键
     * @param permits 动态额度
     * @return 活动业务数
     * @throws InterruptedException 业务被中断
     */
    @GetMapping("/permits")
    public ApiResponse<Map<String, Long>> permits(@RequestParam String key, @RequestParam(defaultValue = "1") int permits)
            throws InterruptedException {
        return ApiResponse.ok(Map.of("active", service.permits(key, permits)));
    }

    /**
     * 查询共享活动数。
     *
     * @param key 业务键
     * @return 活动数
     */
    @GetMapping("/active")
    public ApiResponse<Map<String, Long>> active(@RequestParam String key) {
        return ApiResponse.ok(Map.of("active", service.active(key)));
    }

    /**
     * 锁竞争或所有权丢失映射为 HTTP 423。
     *
     * @return 拒绝响应
     */
    @ExceptionHandler(LockException.class)
    public ResponseEntity<ApiResponse<Void>> rejected() {
        return ResponseEntity.status(423).body(ApiResponse.failOfMessage("未获取锁或锁已丢失", 423));
    }

    /**
     * 不合法规则映射为 HTTP 400。
     *
     * @return 参数错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400));
    }

    /**
     * 业务模拟失败映射为 HTTP 500。
     *
     * @return 失败响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> failed() {
        return ResponseEntity.internalServerError().body(ApiResponse.fail(500));
    }
}
