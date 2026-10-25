package io.github.bytex0.example.controller;

import io.github.bytex0.common.log.model.OperationLogInfo;
import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.example.audit.AuditInput;
import io.github.bytex0.example.audit.AuditScenarioService;
import io.github.bytex0.example.audit.ExampleOperationLogHandler;
import io.github.bytex0.exception.BizException;
import io.github.bytex0.util.TraceIdUtil;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志验证接口，只回传经过切面过滤的日志，不回传原始内部敏感结果。
 *
 * @author bytex0
 * @since 2026-10-06 19:10:52
 */
@RestController
public class AuditCapabilityController {

    /**
     * 由 Starter 切面增强的业务服务。
     */
    private final AuditScenarioService service;

    /**
     * 用户覆盖的同步示例处理器。
     */
    private final ExampleOperationLogHandler handler;

    /**
     * 注入代理服务和示例处理器。
     *
     * @param service 代理服务
     * @param handler 示例日志处理器
     */
    public AuditCapabilityController(AuditScenarioService service, ExampleOperationLogHandler handler) {
        this.service = service;
        this.handler = handler;
    }

    /**
     * 执行固定场景并返回实际日志，退出时恢复 MDC 和清理示例状态。
     *
     * @param mode success、failure、no-payload、large 或 handler-failure
     * @param input 已校验测试输入
     * @return 实际业务状态及已过滤日志
     */
    @PostMapping("/api/common/audit")
    public ResponseEntity<ApiResponse<OperationLogInfo>> run(@RequestParam(defaultValue = "success") String mode,
                                                            @Valid @RequestBody AuditInput input) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        handler.clear();
        TraceIdUtil.setTraceId(TraceIdUtil.generateTraceId());
        try {
            switch (mode) {
                case "success" -> service.success(input);
                case "failure" -> service.failure(input);
                case "no-payload" -> service.noPayload(input);
                case "large" -> service.large(input);
                case "handler-failure" -> {
                    handler.fail(true);
                    service.success(input);
                }
                default -> throw new BizException("unknown fixture mode");
            }
            return ResponseEntity.ok(ApiResponse.ok(handler.current()));
        } catch (BizException exception) {
            ApiResponse<OperationLogInfo> response = ApiResponse.failOfMessage(exception.getMessage(), 400);
            response.setData(handler.current());
            return ResponseEntity.badRequest().body(response);
        } finally {
            handler.clear();
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
