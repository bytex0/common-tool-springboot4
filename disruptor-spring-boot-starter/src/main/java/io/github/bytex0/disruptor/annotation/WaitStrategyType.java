package io.github.bytex0.disruptor.annotation;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.PhasedBackoffWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.TimeoutBlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

import java.util.concurrent.TimeUnit;

/**
 * 消费等待策略(WaitStrategyType)保留原七种 LMAX 策略，每次创建独立实例。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
public enum WaitStrategyType {

    /**
     * 无数据时阻塞，适合通用业务。
     */
    BLOCKING,

    /**
     * 让出 CPU 并轮询，需要充足 CPU 资源。
     */
    YIELDING,

    /**
     * 忙自旋，适合独占 CPU 的极低延迟场景，不适合普通虚拟线程任务。
     */
    BUSY_SPIN,

    /**
     * 逐步让出和短暂停顿，以延迟换取较低 CPU 使用。
     */
    SLEEPING,

    /**
     * 阻塞最多一秒后触发 LMAX 超时事件，不表示业务执行超时。
     */
    TIMEOUT_BLOCKING,

    /**
     * 轻量阻塞策略。
     */
    LITE_BLOCKING,

    /**
     * 先自旋 100 纳秒，再让出至 1000 纳秒，最后轻量阻塞；参数是时间而非循环次数。
     */
    PHASED_BACKOFF;

    /**
     * 创建当前队列独享的等待策略。
     *
     * @return LMAX 策略
     */
    public WaitStrategy create() {
        return switch (this) {
            case BLOCKING -> new BlockingWaitStrategy();
            case YIELDING -> new YieldingWaitStrategy();
            case BUSY_SPIN -> new BusySpinWaitStrategy();
            case SLEEPING -> new SleepingWaitStrategy();
            case TIMEOUT_BLOCKING -> new TimeoutBlockingWaitStrategy(1, TimeUnit.SECONDS);
            case LITE_BLOCKING -> new LiteBlockingWaitStrategy();
            case PHASED_BACKOFF -> new PhasedBackoffWaitStrategy(100, 1000, TimeUnit.NANOSECONDS, new LiteBlockingWaitStrategy());
        };
    }
}
