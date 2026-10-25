package io.github.bytex0.common.log.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 属性方式激活操作日志的 Boot 4 入口，未启用时不创建切面。
 *
 * @author bytex0
 * @since 2026-10-06 19:01:01
 */
@AutoConfiguration
@ConditionalOnClass(name = {"org.aspectj.lang.ProceedingJoinPoint", "tools.jackson.databind.json.JsonMapper"})
@ConditionalOnProperty(prefix = "common-tool.operation-log", name = "enabled", havingValue = "true")
@ImportAutoConfiguration(OperationLogAutoConfiguration.class)
public class OperationLogActivationAutoConfiguration {
}
