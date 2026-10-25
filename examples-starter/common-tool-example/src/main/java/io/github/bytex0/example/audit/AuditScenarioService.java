package io.github.bytex0.example.audit;

import io.github.bytex0.common.log.annotation.OperationLog;
import io.github.bytex0.common.log.annotation.OperationType;
import io.github.bytex0.exception.BizException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 同步操作日志场景，通过 Spring 代理执行，不在控制器中复制切面实现。
 *
 * @author bytex0
 * @since 2026-10-06 19:10:52
 */
@Service
public class AuditScenarioService {

    /**
     * 大响应测试长度，超过默认日志捕获限制。
     */
    private static final int LARGE_RESPONSE_LENGTH = 10000;

    /**
     * 返回包含敏感字段的内部业务结果，外层接口不会回传该原始结果。
     *
     * @param input 已由控制器绑定和校验的请求体
     * @return 用于验证响应遮蔽的内部结果
     */
    @OperationLog(type = OperationType.CREATE, description = "fixture create")
    public Map<String, String> success(@RequestBody AuditInput input) {
        return Map.of("name", input.name(), "access_token", input.token());
    }

    /**
     * 抛出固定业务异常，验证失败日志及异常传播。
     *
     * @param input 已绑定请求体
     */
    @OperationLog(type = OperationType.UPDATE, description = "fixture failure")
    public void failure(@RequestBody AuditInput input) {
        throw new BizException("fixture business failure");
    }

    /**
     * 显式关闭请求和响应捕获。
     *
     * @param input 已绑定请求体
     * @return 固定业务结果
     */
    @OperationLog(type = OperationType.QUERY, saveRequestData = false, saveResponseData = false)
    public String noPayload(@RequestBody AuditInput input) {
        return "ok";
    }

    /**
     * 返回大结果，验证日志有界捕获，不直接输出到 HTTP 响应。
     *
     * @param input 已绑定请求体
     * @return 超过默认捕获上限的内部结果
     */
    @OperationLog(type = OperationType.EXPORT)
    public String large(@RequestBody AuditInput input) {
        return "x".repeat(LARGE_RESPONSE_LENGTH);
    }
}
