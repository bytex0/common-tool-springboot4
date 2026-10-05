package io.github.bytex0.disruptor;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import io.github.bytex0.disruptor.config.DisruptorProperties;
import io.github.bytex0.disruptor.core.DisruptorEngine;
import io.github.bytex0.disruptor.monitor.DisruptorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 注册受容器生命周期管理的消息队列模板。
 *
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
@AutoConfiguration
@EnableConfigurationProperties(DisruptorProperties.class)
@ConditionalOnProperty(prefix = "disruptor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DisruptorAutoConfiguration {

    /**
     * 保留此前直接工厂调用入口。
     *
     * @param handlers 处理器
     * @param bufferSize 容量
     * @return 类型化模板
     */
    public DisruptorTemplate disruptorTemplate(List<MessageHandler<?>> handlers, int bufferSize) {
        return new DisruptorTemplate(handlers, bufferSize);
    }

    /**
     * 注册可替换的类型化模板和共享引擎。
     *
     * @param handlers 业务处理器
     * @param properties 全局配置
     * @param metrics 可选监控来源
     * @return 模板
     */
    @Bean
    @ConditionalOnMissingBean(DisruptorEngine.class)
    public DisruptorTemplate disruptorTemplate(List<MessageHandler<?>> handlers, DisruptorProperties properties,
                                               ObjectProvider<DisruptorMetrics> metrics) {
        return new DisruptorTemplate(handlers, properties, metrics.getIfAvailable());
    }

    /**
     * 显式开启监控时要求应用提供注册表，不创建或关闭外部注册表。
     *
     * @param registry 应用指标注册表
     * @return 指标组件
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "disruptor", name = "enable-metrics", havingValue = "true")
    public DisruptorMetrics disruptorMetrics(MeterRegistry registry) {
        return new DisruptorMetrics(registry);
    }
}
