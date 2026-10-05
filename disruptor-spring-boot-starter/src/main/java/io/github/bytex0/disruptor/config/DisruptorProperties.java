package io.github.bytex0.disruptor.config;

import com.lmax.disruptor.dsl.ProducerType;
import io.github.bytex0.disruptor.annotation.WaitStrategyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 队列配置(DisruptorProperties)恢复原默认值，并补充有界发布及停止策略。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("disruptor")
public class DisruptorProperties {

    /**
     * 是否装配队列能力，默认 true。
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 默认环容量，1024，必须为 2 至 1048576 内的二次幂。
     */
    @Builder.Default
    private int bufferSize = 1024;

    /**
     * 默认生产者模式，MULTI。
     */
    @Builder.Default
    private ProducerType producerType = ProducerType.MULTI;

    /**
     * 默认消费等待策略，BLOCKING。
     */
    @Builder.Default
    private WaitStrategyType waitStrategy = WaitStrategyType.BLOCKING;

    /**
     * 是否注册 Micrometer 指标，默认 false；开启时须提供 MeterRegistry。
     */
    @Builder.Default
    private boolean enableMetrics = false;

    /**
     * 默认工作线程数，1；多线程不保证消息完成顺序。
     */
    @Builder.Default
    private int threads = 1;

    /**
     * 默认使用虚拟线程，true；手工指定 ThreadFactory 时以调用方工厂为准。
     */
    @Builder.Default
    private boolean virtualThread = true;

    /**
     * 当前模板最多注册队列数，默认 64。
     */
    @Builder.Default
    private int maxQueues = 64;

    /**
     * 原 void send 入口在满队列时最多等待，默认 5 秒，范围 0 至 1 分钟，零表示立即拒绝。
     */
    @Builder.Default
    private Duration publishTimeout = Duration.ofSeconds(5);

    /**
     * 单队列优雅停止等待，默认 5 秒，范围 1 毫秒至 1 分钟；超时后使未完成确认失败。
     */
    @Builder.Default
    private Duration shutdownTimeout = Duration.ofSeconds(5);
}
