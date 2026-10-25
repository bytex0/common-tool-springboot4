package io.github.bytex0.common.log.config;

import io.github.bytex0.common.log.annotation.EnableOperationLog;
import io.github.bytex0.common.log.aspect.OperationLogAspect;
import io.github.bytex0.common.log.service.LoggingOperationLogHandler;
import io.github.bytex0.common.log.service.OperationLogHandler;
import io.github.bytex0.common.log.service.OperationLogSanitizer;
import io.github.bytex0.common.log.service.OperationRequestAccessor;
import io.github.bytex0.common.log.service.OperatorInfoProvider;
import io.github.bytex0.common.log.service.WebOperationRequestAccessor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 注解导入的操作日志装配，使用 Spring 注解 API 读取自定义类型，构造失败明确传播。
 *
 * @author bytex0
 * @since 2026-10-06 19:01:01
 */
@AutoConfiguration
@ConditionalOnClass(name = {"org.aspectj.lang.ProceedingJoinPoint", "tools.jackson.databind.json.JsonMapper"})
@ConditionalOnProperty(prefix = "common-tool", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "common-tool.operation-log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OperationLogProperties.class)
@EnableAspectJAutoProxy
@Import(OperationLogAutoConfiguration.WebConfiguration.class)
public class OperationLogAutoConfiguration implements ImportAware {

    /**
     * 直接注解导入时的启用配置，纯属性启用时为空。
     */
    private EnableOperationLog annotation;

    /**
     * 读取真实注解元数据，不手工实现注解代理或进行无检查的 Class 转型。
     *
     * @param metadata 导入方元数据
     */
    @Override
    public void setImportMetadata(AnnotationMetadata metadata) {
        if (metadata.getAnnotations().isPresent(EnableOperationLog.class)) {
            annotation = metadata.getAnnotations().get(EnableOperationLog.class).synthesize();
        }
    }

    /**
     * 创建默认或注解指定处理器，已有用户 Bean 优先。
     *
     * @param properties 输出配置
     * @return 日志处理器
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogHandler.class)
    public OperationLogHandler operationLogHandler(OperationLogProperties properties) {
        if (annotation != null && annotation.handler() != OperationLogHandler.class) {
            return BeanUtils.instantiateClass(annotation.handler());
        }
        return new LoggingOperationLogHandler(properties.isLogPayloads());
    }

    /**
     * 创建默认或注解指定操作者提供者，已有用户 Bean 优先。
     *
     * @return 操作者提供者
     */
    @Bean
    @ConditionalOnMissingBean(OperatorInfoProvider.class)
    public OperatorInfoProvider operatorInfoProvider() {
        if (annotation != null && annotation.provider() != OperatorInfoProvider.class) {
            return BeanUtils.instantiateClass(annotation.provider());
        }
        return OperatorInfoProvider.DEFAULT;
    }

    /**
     * 创建有界捕获过滤器。
     *
     * @param properties 安全与容量配置
     * @return 捕获器
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationLogSanitizer operationLogSanitizer(OperationLogProperties properties) {
        return new OperationLogSanitizer(properties);
    }

    /**
     * 创建切面，非 Web 环境不加载 Servlet 适配器。
     *
     * @param handler 处理器
     * @param provider 操作者提供者
     * @param sanitizer 捕获器
     * @param accessors 可选请求适配器
     * @param properties 元数据容量配置
     * @return 操作日志切面
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(OperationLogHandler handler, OperatorInfoProvider provider,
            OperationLogSanitizer sanitizer, ObjectProvider<OperationRequestAccessor> accessors,
            OperationLogProperties properties) {
        return new OperationLogAspect(handler, provider, sanitizer,
                accessors.getIfAvailable(() -> OperationRequestAccessor.NONE), properties.getMaxMetadataLength());
    }

    /**
     * 可选 Servlet 适配配置。
     *
     * @author bytex0
     * @since 2026-10-06 19:01:01
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"jakarta.servlet.http.HttpServletRequest",
            "org.springframework.web.context.request.RequestContextHolder"})
    static class WebConfiguration {

        /**
         * 创建请求元数据适配器。
         *
         * @param properties 长度限制
         * @return Web 适配器
         */
        @Bean
        @ConditionalOnMissingBean(OperationRequestAccessor.class)
        OperationRequestAccessor operationRequestAccessor(OperationLogProperties properties) {
            return new WebOperationRequestAccessor(properties.getMaxMetadataLength(), properties.isTrustProxyHeaders());
        }
    }
}
