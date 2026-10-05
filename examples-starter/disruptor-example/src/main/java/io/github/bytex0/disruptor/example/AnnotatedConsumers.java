package io.github.bytex0.disruptor.example;

import io.github.bytex0.disruptor.annotation.DisruptorListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 注解消费者(AnnotatedConsumers)观察真实工作线程数、线程类型及单次处理副作用。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:35:33
 */
@Component
public class AnnotatedConsumers {

    /**
     * 为并发观察保留的短业务延时，毫秒。
     */
    private static final long PROCESSING_DELAY_MILLIS = 50;

    /**
     * 当前活动处理数量。
     */
    private final AtomicInteger active = new AtomicInteger();

    /**
     * 观察到的最大并发。
     */
    private final AtomicInteger maximum = new AtomicInteger();

    /**
     * 平台线程监听器处理次数。
     */
    private final AtomicLong processed = new AtomicLong();

    /**
     * 平台线程监听器累计结果。
     */
    private final AtomicLong total = new AtomicLong();

    /**
     * 平台线程监听器实际线程模式。
     */
    private final Set<Boolean> modes = ConcurrentHashMap.newKeySet();

    /**
     * 虚拟线程监听器累计结果。
     */
    private final AtomicLong virtualTotal = new AtomicLong();

    /**
     * 虚拟线程监听器实际观察到的线程模式。
     */
    private final AtomicBoolean virtualMode = new AtomicBoolean();

    /**
     * 两个平台工作线程各自消费分配的消息，负值模拟失败且不产生副作用。
     *
     * @param value 非负业务值
     * @throws InterruptedException 停机中断
     */
    @DisruptorListener(value = "annotated", threads = 2, virtualThread = false, bufferSize = 16, inheritDefaults = false)
    public void platform(long value) throws InterruptedException {
        if (value < 0) {
            throw new IllegalStateException("negative value");
        }
        int current = active.incrementAndGet();
        maximum.accumulateAndGet(current, Math::max);
        modes.add(Thread.currentThread().isVirtual());
        try {
            Thread.sleep(PROCESSING_DELAY_MILLIS);
            total.addAndGet(value);
            processed.incrementAndGet();
        } finally {
            active.decrementAndGet();
        }
    }

    /**
     * 独立虚拟线程监听，验证注解模式实际生效。
     *
     * @param value 业务值
     */
    @DisruptorListener(value = "virtual", virtualThread = true, bufferSize = 16, inheritDefaults = false)
    public void virtual(long value) {
        virtualMode.set(Thread.currentThread().isVirtual());
        virtualTotal.addAndGet(value);
    }

    /**
     * 获取不包含消息正文的观察统计。
     *
     * @return 普通 JSON 可序列化快照
     */
    public Map<String, Object> snapshot() {
        return Map.of("processed", processed.get(), "total", total.get(), "maxActive", maximum.get(),
                "virtualThreads", Set.copyOf(modes), "virtualTotal", virtualTotal.get(), "virtualMode", virtualMode.get());
    }
}
