package io.github.bytex0.disruptor;

import io.github.bytex0.disruptor.config.DisruptorProperties;
import io.github.bytex0.disruptor.core.DisruptorEngine;
import io.github.bytex0.disruptor.monitor.DisruptorMetrics;

import java.util.List;

/**
 * 有界多生产者消息队列，消费确认、拒绝和停机行为对调用方可见。
 *
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
public class DisruptorTemplate extends DisruptorEngine {

    /**
     * 保留当前处理器列表与容量构造器。
     *
     * @param handlers 类型化处理器
     * @param bufferSize 容量
     */
    public DisruptorTemplate(List<MessageHandler<?>> handlers, int bufferSize) {
        this(handlers, DisruptorProperties.builder().bufferSize(bufferSize).build(), null);
    }

    /**
     * 使用完整配置和可选监控。
     *
     * @param handlers 处理器列表
     * @param properties 配置
     * @param metrics 可选监控
     */
    public DisruptorTemplate(List<MessageHandler<?>> handlers, DisruptorProperties properties, DisruptorMetrics metrics) {
        super(handlers, properties, metrics);
    }
}
