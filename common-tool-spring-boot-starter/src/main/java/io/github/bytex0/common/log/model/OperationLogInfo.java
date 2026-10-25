package io.github.bytex0.common.log.model;

import io.github.bytex0.common.log.annotation.OperationType;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 操作日志模型，保留原字段和链式访问器；不可用的上下文信息保持 null。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
@Data
@Accessors(chain = true)
public class OperationLogInfo {

    /**
     * 追踪标识，优先使用 requestId MDC 键，兼容旧 traceId 键。
     */
    private String traceId;

    /**
     * 操作类型。
     */
    private OperationType operationType;

    /**
     * 注解声明的操作描述。
     */
    private String description;

    /**
     * 请求路径，不包含查询参数。
     */
    private String requestUri;

    /**
     * HTTP 方法，非 Web 调用为空。
     */
    private String requestMethod;

    /**
     * 已过滤的方法参数 JSON，保持数组形式；未采集时为空。
     */
    private String requestParams;

    /**
     * 已过滤的绑定请求体 JSON，不读取原始 Servlet 输入流。
     */
    private String requestBody;

    /**
     * 已过滤的响应 JSON，超限或不可序列化时使用明确省略标记。
     */
    private String responseData;

    /**
     * 实际对端地址，不默认信任可伪造代理头。
     */
    private String requestIp;

    /**
     * User-Agent，按配置限制长度。
     */
    private String userAgent;

    /**
     * 操作人标识，默认 unknown。
     */
    private String operatorId;

    /**
     * 操作人名称，默认 unknown。
     */
    private String operatorName;

    /**
     * 操作人部门标识，默认 unknown。
     */
    private String operatorDeptId;

    /**
     * 操作人部门名称，默认 unknown。
     */
    private String operatorDeptName;

    /**
     * 开始时间戳，单位毫秒。
     */
    private Long startTime;

    /**
     * 结束时间戳，单位毫秒。
     */
    private Long endTime;

    /**
     * 单调时钟计算的执行耗时，单位毫秒，不受系统时钟回拨影响。
     */
    private Long duration;

    /**
     * 操作结果，0 表示成功，1 表示失败。
     */
    private Integer status;

    /**
     * 业务失败类型，不复制可能含凭据的原始异常消息。
     */
    private String errorMessage;
}
