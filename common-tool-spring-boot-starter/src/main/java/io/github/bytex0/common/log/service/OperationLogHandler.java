package io.github.bytex0.common.log.service;

import io.github.bytex0.common.log.model.OperationLogInfo;
import java.util.Objects;

/**
 * 操作日志处理器，接收已经脱敏及限制大小的日志模型。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
public interface OperationLogHandler {

    /**
     * 处理一次操作日志，实现不得假设请求和响应内容始终可用。
     *
     * @param operationLogInfo 非空日志信息
     */
    void handleLog(OperationLogInfo operationLogInfo);

    /**
     * 默认返回完整模型供调用方继续转换，不再以 null 表示未实现。
     *
     * @param operationLogInfo 非空日志信息
     * @return 默认日志对象
     */
    default Object convertLog(OperationLogInfo operationLogInfo) {
        return Objects.requireNonNull(operationLogInfo, "operationLogInfo");
    }
}
