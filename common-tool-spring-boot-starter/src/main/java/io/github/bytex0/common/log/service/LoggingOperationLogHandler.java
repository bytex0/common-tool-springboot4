package io.github.bytex0.common.log.service;

import io.github.bytex0.common.log.model.OperationLogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置占位符日志处理器，默认不输出请求与响应正文。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
public class LoggingOperationLogHandler implements OperationLogHandler {

    /**
     * 操作日志输出器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOperationLogHandler.class);

    /**
     * 是否输出已经过过滤的正文。
     */
    private final boolean logPayloads;

    /**
     * 配置内置日志输出范围。
     *
     * @param logPayloads 是否输出经过默认过滤器处理的正文
     */
    public LoggingOperationLogHandler(boolean logPayloads) {
        this.logPayloads = logPayloads;
    }

    /**
     * 输出操作结果，正文仅在显式开启时输出；输入必须已经过采集过滤。
     *
     * @param info 非空操作日志
     */
    @Override
    public void handleLog(OperationLogInfo info) {
        LOGGER.info("Operation: type={}, description={}, status={}, durationMs={}, traceId={}, operatorId={}",
                info.getOperationType(), info.getDescription(), info.getStatus(), info.getDuration(),
                info.getTraceId(), info.getOperatorId());
        if (logPayloads) {
            LOGGER.info("Operation payload: request={}, body={}, response={}",
                    info.getRequestParams(), info.getRequestBody(), info.getResponseData());
        }
    }
}
