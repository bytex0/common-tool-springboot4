package io.github.bytex0.disruptor.monitor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import io.github.bytex0.disruptor.event.DisruptorEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 队列指标(DisruptorMetrics)保留原指标名称，按实际实例注销，避免同名重建后保留旧环。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
public class DisruptorMetrics implements AutoCloseable {

    /**
     * 外部注册表，不由组件关闭。
     */
    private final MeterRegistry registry;

    /**
     * 本组件登记的指标，不删除其他组件的指标。
     */
    private final Map<String, Registration> registrations = new HashMap<>();

    /**
     * 仅保护本地指标注册与移除，不等待消费线程。
     */
    private final ReentrantLock mutation = new ReentrantLock();

    /**
     * 关闭后不再接受迟到的注册，仍允许幂等注销。
     */
    private boolean closed;

    /**
     * 保留原注册表构造器。
     *
     * @param registry 业务注册表
     */
    public DisruptorMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    /**
     * 保留原手工指标注册入口。
     *
     * @param queueName 队列名称
     * @param disruptor 队列实例，为 null 时忽略
     */
    public void registerManualDisruptor(String queueName, Disruptor<DisruptorEvent<Object>> disruptor) {
        register(queueName, disruptor);
    }

    /**
     * 注册任意事件类型，同名替换只移除本组件原先登记的指标。
     *
     * @param queueName 非空名称
     * @param disruptor 队列实例
     */
    public void register(String queueName, Disruptor<?> disruptor) {
        Assert.hasText(queueName, "队列名称不能为空");
        if (disruptor == null) {
            return;
        }
        mutation.lock();
        try {
            Assert.state(!closed, "队列指标组件已关闭");
            Registration old = registrations.get(queueName);
            if (old != null && old.disruptor() == disruptor) {
                return;
            }
            if (old != null) {
                old.meters().forEach(registry::remove);
                registrations.remove(queueName);
            }
            Assert.state(registry.find("disruptor.buffer.size").tag("queue", queueName).meter() == null
                    && registry.find("disruptor.remaining.capacity").tag("queue", queueName).meter() == null,
                    "队列指标名称已被其他组件使用");
            RingBuffer<?> ring = disruptor.getRingBuffer();
            List<Meter> created = new ArrayList<>();
            try {
                created.add(Gauge.builder("disruptor.buffer.size", ring, RingBuffer::getBufferSize)
                        .tag("queue", queueName).register(registry));
                created.add(Gauge.builder("disruptor.remaining.capacity", ring, RingBuffer::remainingCapacity)
                        .tag("queue", queueName).register(registry));
                registrations.put(queueName, new Registration(disruptor, List.copyOf(created)));
            } catch (RuntimeException exception) {
                created.forEach(registry::remove);
                throw exception;
            }
        } finally {
            mutation.unlock();
        }
    }

    /**
     * 只注销指定实例，旧队列迟到的关闭不能移除新队列指标。
     *
     * @param name 名称
     * @param expected 原队列实例
     */
    public void unregister(String name, Disruptor<?> expected) {
        mutation.lock();
        try {
            Registration registration = registrations.get(name);
            if (registration != null && registration.disruptor() == expected) {
                registration.meters().forEach(registry::remove);
                registrations.remove(name);
            }
        } finally {
            mutation.unlock();
        }
    }

    /**
     * 移除全部本组件创建的指标，包括手工登记项，不关闭应用注册表。
     */
    @Override
    public void close() {
        mutation.lock();
        try {
            closed = true;
            registrations.values().forEach(registration -> registration.meters().forEach(registry::remove));
            registrations.clear();
        } finally {
            mutation.unlock();
        }
    }

    /**
     * 指标归属(Registration)固定当前环与创建的仪表。
     *
     * @author linshiqiang
     * @since 2026-10-06 03:17:49
     * @param disruptor 对应实例
     * @param meters 本组件创建的仪表
     */
    private record Registration(
            /**
             * 对应队列实例。
             */
            Disruptor<?> disruptor,

            /**
             * 需要共同注销的仪表。
             */
            List<Meter> meters) {
    }
}
