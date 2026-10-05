package io.github.bytex0.threadpool;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * 支持显式关闭和任务上下文装饰器的线程池自动配置。
 *
 * @author bytex0
 * @since 2026-10-05 20:05:22
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "dynamic-threadpool", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ThreadPoolAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolRegistry threadPoolRegistry(ThreadPoolProperties properties, ObjectProvider<TaskDecorator> decorators) {
        return new ThreadPoolRegistry(properties, decorators.getIfAvailable(() -> task -> task));
    }
}
