package io.github.bytex0.disruptor.config;

import io.github.bytex0.disruptor.core.DisruptorEngine;
import io.github.bytex0.disruptor.monitor.DisruptorMetrics;
import io.github.bytex0.disruptor.processor.DisruptorListenerProcessor;
import io.github.bytex0.disruptor.template.DisruptorTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

/**
 * 原配置入口(DisruptorAutoConfiguration)恢复模板、监听器和指标工厂，使用共享引擎避免重复队列。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
@AutoConfiguration(afterName = "io.github.bytex0.disruptor.DisruptorAutoConfiguration")
@ConditionalOnProperty(prefix = "disruptor", name = "enabled", havingValue = "true", matchIfMissing = true)
@Role(ROLE_INFRASTRUCTURE)
public class DisruptorAutoConfiguration {

    /**
     * 注册原包路径模板，Bean 名与现代模板区分。
     *
     * @param engine 当前共享引擎
     * @return 原模板接口
     */
    @Bean
    @ConditionalOnMissingBean
    public DisruptorTemplate legacyDisruptorTemplate(DisruptorEngine engine) {
        return new DisruptorTemplate(engine, true);
    }

    /**
     * 保留原可选指标工厂方法。
     *
     * @param metrics 可选监控
     * @return 独立模板
     */
    public DisruptorTemplate disruptorTemplate(DisruptorMetrics metrics) {
        return new DisruptorTemplate(metrics);
    }

    /**
     * 保留原指标工厂方法，自动 Bean 由现代配置统一注册。
     *
     * @param registry 指标注册表
     * @return 指标组件
     */
    public DisruptorMetrics disruptorMetrics(MeterRegistry registry) {
        return new DisruptorMetrics(registry);
    }

    /**
     * 静态工厂只注入提供器，避免实例化 BeanPostProcessor 时提前创建业务队列。
     *
     * @param templates 原模板提供器
     * @param properties 全局配置提供器
     * @return 监听器处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public static DisruptorListenerProcessor disruptorListenerProcessor(ObjectProvider<DisruptorTemplate> templates,
                                                                        ObjectProvider<DisruptorProperties> properties) {
        return new DisruptorListenerProcessor(templates::getObject, properties::getObject);
    }

    /**
     * 保留原直接模板参数构造工厂。
     *
     * @param template 原模板
     * @return 监听器处理器
     */
    public DisruptorListenerProcessor disruptorListenerProcessor(DisruptorTemplate template) {
        return new DisruptorListenerProcessor(template);
    }
}
