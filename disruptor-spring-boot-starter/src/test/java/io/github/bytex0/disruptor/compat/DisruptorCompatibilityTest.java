package io.github.bytex0.disruptor.compat;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.github.bytex0.disruptor.annotation.DisruptorListener;
import io.github.bytex0.disruptor.annotation.WaitStrategyType;
import io.github.bytex0.disruptor.config.DisruptorProperties;
import io.github.bytex0.disruptor.core.DisruptorEngine;
import io.github.bytex0.disruptor.event.DisruptorEvent;
import io.github.bytex0.disruptor.factory.DisruptorEventFactory;
import io.github.bytex0.disruptor.monitor.DisruptorMetrics;
import io.github.bytex0.disruptor.processor.DisruptorListenerProcessor;
import io.github.bytex0.disruptor.template.DisruptorTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 原队列兼容(DisruptorCompatibilityTest)覆盖原 API、实际线程策略、代理调用和指标生命周期。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:28:05
 */
class DisruptorCompatibilityTest {

    /**
     * 七种策略每次产生独立实例，事件工厂保留原模型操作。
     */
    @Test
    void restoresStrategiesAndEventFactory() {
        assertThat(WaitStrategyType.values()).hasSize(7);
        for (WaitStrategyType strategy : WaitStrategyType.values()) {
            assertThat(strategy.create()).isNotNull().isNotSameAs(strategy.create());
        }
        DisruptorEventFactory<String> factory = new DisruptorEventFactory<>();
        DisruptorEvent<String> event = factory.newInstance();
        event.setData("value");
        assertThat(event.getData()).isEqualTo("value");
        assertThat(factory.newInstance()).isNotSameAs(event);
        event.clear();
        assertThat(event.getData()).isNull();
    }

    /**
     * 原 SINGLE 工厂支持多个发布线程，关闭后可重建同名队列且指标指向新环。
     *
     * @throws Exception 等待消息失败
     */
    @Test
    void supportsOriginalFactoriesSingleProducerAndMetricRebuild() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (DisruptorTemplate template = new DisruptorTemplate(new DisruptorMetrics(registry));
             ThreadPoolExecutor producers = producers()) {
            Set<Integer> values = ConcurrentHashMap.newKeySet();
            template.<Integer>createQueue("manual", 128, ProducerType.SINGLE, WaitStrategyType.LITE_BLOCKING.create(),
                    Thread.ofVirtual().factory(), event -> assertThat(values.add(event.getData())).isTrue());
            List<Future<?>> sends = new ArrayList<>();
            for (int value = 0; value < 50; value++) {
                int payload = value;
                sends.add(producers.submit(() -> template.send("manual", payload)));
            }
            for (Future<?> send : sends) {
                send.get(3, TimeUnit.SECONDS);
            }
            template.sendAsync("manual", 99).get(3, TimeUnit.SECONDS);
            assertThat(values).hasSize(51);
            assertThat(registry.get("disruptor.buffer.size").tag("queue", "manual").gauge().value()).isEqualTo(128);
            assertThatThrownBy(() -> template.createQueue("manual", 8, ProducerType.MULTI, WaitStrategyType.BLOCKING.create(),
                    Thread.ofVirtual().factory(), event -> { })).hasMessageContaining("Duplicate");
            template.shutdown("manual");
            assertThat(registry.find("disruptor.buffer.size").tag("queue", "manual").gauge()).isNull();
            template.createQueue("manual", 8, ProducerType.MULTI, WaitStrategyType.BLOCKING.create(),
                    Thread.ofVirtual().factory(), event -> { });
            assertThat(registry.get("disruptor.buffer.size").tag("queue", "manual").gauge().value()).isEqualTo(8);
            template.shutdownAll();
            template.createQueue("again", 2, ProducerType.MULTI, WaitStrategyType.SLEEPING.create(),
                    Thread.ofVirtual().factory(), event -> { });
            template.sendAsync("again", "ok").get(2, TimeUnit.SECONDS);
        } finally {
            registry.close();
        }
    }

    /**
     * 原生注册不改写外部消费链，原 void send 可用，但不伪造消费完成确认。
     *
     * @throws Exception 等待消费失败
     */
    @Test
    void preservesRawDisruptorRegistrationWithoutInventedAcknowledgement() throws Exception {
        CountDownLatch consumed = new CountDownLatch(1);
        Disruptor<DisruptorEvent<Object>> raw = new Disruptor<>(new DisruptorEventFactory<>(), 8,
                Thread.ofVirtual().factory(), ProducerType.MULTI, WaitStrategyType.BLOCKING.create());
        raw.handleEventsWith((EventHandler<DisruptorEvent<Object>>) (event, sequence, end) -> consumed.countDown());
        raw.start();
        try (DisruptorTemplate template = new DisruptorTemplate(null)) {
            template.registerDisruptor("external", raw);
            template.send("external", "value");
            assertThat(consumed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> template.sendAsync("external", "value")).hasMessageContaining("不提供消费确认");
        } finally {
            raw.halt();
        }
    }

    /**
     * 注解线程数真实并行且每条消息只消费一次，false 虚拟线程选项生效。
     *
     * @throws Exception 等待消费失败
     */
    @Test
    void honorsAnnotationWorkersAndPreservesBusinessException() throws Exception {
        DisruptorProperties properties = new DisruptorProperties();
        ParallelListener listener = new ParallelListener();
        try (DisruptorEngine engine = new DisruptorEngine(List.of(), properties, null)) {
            DisruptorTemplate template = new DisruptorTemplate(engine, true);
            DisruptorListenerProcessor processor = new DisruptorListenerProcessor(() -> template, () -> properties);
            processor.postProcessAfterInitialization(listener, "listener");
            processor.afterSingletonsInstantiated();
            CompletableFuture<Void> first = template.sendAsync("annotated", 1L);
            CompletableFuture<Void> second = template.sendAsync("annotated", 2L);
            try {
                assertThat(listener.entered.await(2, TimeUnit.SECONDS)).isTrue();
            } finally {
                listener.release.countDown();
            }
            CompletableFuture.allOf(first, second).get(2, TimeUnit.SECONDS);
            assertThat(listener.values).containsExactlyInAnyOrder(1L, 2L);
            assertThat(listener.virtualThreads).containsExactly(false);
            assertThat(template.stats("annotated").get("threads")).isEqualTo(2);
            assertThatThrownBy(() -> template.sendAsync("annotated", -1L).get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            listener.release.countDown();
        }
    }

    /**
     * 全局默认值应用到保持默认属性的注解，修复原配置类未实际使用的问题。
     *
     * @throws Exception 等待确认失败
     */
    @Test
    void appliesGlobalDefaultsAndPreservesProxyInvocation() throws Exception {
        DisruptorProperties properties = DisruptorProperties.builder().bufferSize(16).threads(2).virtualThread(false)
                .producerType(ProducerType.SINGLE).waitStrategy(WaitStrategyType.LITE_BLOCKING).build();
        AtomicInteger intercepted = new AtomicInteger();
        DefaultListener target = new DefaultListener();
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            intercepted.incrementAndGet();
            return invocation.proceed();
        });
        try (DisruptorEngine engine = new DisruptorEngine(List.of(), properties, null)) {
            DisruptorTemplate template = new DisruptorTemplate(engine, true);
            DisruptorListenerProcessor processor = new DisruptorListenerProcessor(() -> template, () -> properties);
            processor.postProcessAfterInitialization(proxy.getProxy(), "proxiedListener");
            processor.afterSingletonsInstantiated();
            template.sendAsync("defaults", 1L).get(2, TimeUnit.SECONDS);
            assertThat(template.stats("defaults").get("capacity")).isEqualTo(16);
            assertThat(template.stats("defaults").get("threads")).isEqualTo(2);
            assertThat(target.virtual.get()).isFalse();
            assertThat(intercepted).hasValue(1);
        }
    }

    /**
     * 消费者关闭自己不会等待当前线程退出，未完成确认及时失败。
     *
     * @throws Exception 等待确认失败
     */
    @Test
    void avoidsConsumerSelfShutdownDeadlock() throws Exception {
        try (DisruptorEngine engine = new DisruptorEngine(List.of(), new DisruptorProperties(), null)) {
            engine.createQueue("self", 8, ProducerType.MULTI, WaitStrategyType.BLOCKING.create(),
                    Thread.ofVirtual().factory(), 1, String.class, (event, sequence, end) -> engine.shutdown("self"));
            CompletableFuture<Void> pending = engine.send("self", "close");
            assertThatThrownBy(() -> pending.get(1, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RejectedExecutionException.class);
            assertThat(engine.names()).isEmpty();
        }
    }

    /**
     * 停机超时后所有已返回的确认都有终态，不能留下永久悬挂的 Future。
     *
     * @throws Exception 线程协调失败
     */
    @Test
    void terminatesPendingAcknowledgementsOnShutdownTimeout() throws Exception {
        DisruptorProperties properties = DisruptorProperties.builder().shutdownTimeout(Duration.ofMillis(50)).build();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (DisruptorEngine engine = new DisruptorEngine(List.of(), properties, null)) {
            engine.createQueue("blocked", 8, ProducerType.MULTI, WaitStrategyType.BLOCKING.create(),
                    Thread.ofVirtual().factory(), 1, String.class, (event, sequence, end) -> {
                        entered.countDown();
                        release.await();
                    });
            CompletableFuture<Void> first = engine.send("blocked", "one");
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> second = engine.send("blocked", "two");
            engine.close();
            assertThat(first).isDone();
            assertThat(second).isCompletedExceptionally();
        } finally {
            release.countDown();
        }
    }

    /**
     * 并发发布与删除竞争时，所有已经返回给调用方的确认最终完成或失败。
     *
     * @throws Exception 线程协调失败
     */
    @Test
    void settlesAllAcceptedMessagesWhenPublishRacesShutdown() throws Exception {
        List<CompletableFuture<Void>> confirmations = new CopyOnWriteArrayList<>();
        CountDownLatch accepted = new CountDownLatch(1);
        try (DisruptorEngine engine = new DisruptorEngine(List.of(), new DisruptorProperties(), null);
             ThreadPoolExecutor executor = producers()) {
            engine.createQueue("race", 16, ProducerType.SINGLE, WaitStrategyType.BLOCKING.create(),
                    Thread.ofVirtual().factory(), 1, Integer.class, (event, sequence, end) -> { });
            List<Future<?>> publishers = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                publishers.add(executor.submit(() -> {
                    for (int value = 0; value < 100; value++) {
                        try {
                            confirmations.add(engine.send("race", value));
                            accepted.countDown();
                        } catch (IllegalArgumentException | RejectedExecutionException expected) {
                            return;
                        }
                    }
                }));
            }
            assertThat(accepted.await(2, TimeUnit.SECONDS)).isTrue();
            engine.shutdown("race");
            for (Future<?> publisher : publishers) {
                publisher.get(2, TimeUnit.SECONDS);
            }
            assertThat(confirmations).isNotEmpty().allMatch(CompletableFuture::isDone);
        }
    }

    /**
     * 指标组件关闭时清理手工登记项，但不删除其他组件的指标或关闭共享注册表。
     */
    @Test
    void closesOnlyOwnedMetricsAndRejectsLateRegistration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DisruptorMetrics metrics = new DisruptorMetrics(registry);
        Disruptor<DisruptorEvent<Object>> raw = new Disruptor<>(new DisruptorEventFactory<>(), 8,
                Thread.ofVirtual().factory(), ProducerType.MULTI, WaitStrategyType.BLOCKING.create());
        try {
            registry.counter("other.component").increment();
            metrics.registerManualDisruptor("manual-metric", raw);
            metrics.close();
            assertThat(registry.find("disruptor.buffer.size").tag("queue", "manual-metric").gauge()).isNull();
            assertThat(registry.get("other.component").counter().count()).isEqualTo(1);
            assertThat(registry.isClosed()).isFalse();
            assertThatThrownBy(() -> metrics.registerManualDisruptor("late", raw)).hasMessageContaining("已关闭");
        } finally {
            raw.halt();
            metrics.close();
            registry.close();
        }
    }

    /**
     * 使用有界发布测试线程池，避免测试本身引入无限积压。
     *
     * @return 调用方关闭的线程池
     */
    private ThreadPoolExecutor producers() {
        return new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64),
                Thread.ofPlatform().daemon(true).name("disruptor-test-producer-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 并行监听器(ParallelListener)观察真实消费并发与线程类型。
     *
     * @author linshiqiang
     * @since 2026-10-06 03:28:05
     */
    public static class ParallelListener {

        /**
         * 两个处理任务同时进入的信号。
         */
        private final CountDownLatch entered = new CountDownLatch(2);

        /**
         * 控制任务继续执行的信号。
         */
        private final CountDownLatch release = new CountDownLatch(1);

        /**
         * 已消费的唯一数据。
         */
        private final Set<Long> values = ConcurrentHashMap.newKeySet();

        /**
         * 实际观察到的线程类型。
         */
        private final Set<Boolean> virtualThreads = ConcurrentHashMap.newKeySet();

        /**
         * 真实双线程监听，负值模拟受检业务异常。
         *
         * @param value 数据
         * @throws Exception 业务失败或中断
         */
        @DisruptorListener(value = "annotated", threads = 2, virtualThread = false, bufferSize = 8, inheritDefaults = false)
        public void consume(Long value) throws Exception {
            if (value < 0) {
                throw new IOException("business");
            }
            virtualThreads.add(Thread.currentThread().isVirtual());
            entered.countDown();
            release.await();
            assertThat(values.add(value)).isTrue();
        }
    }

    /**
     * 代理接口(ListenerContract)验证监听调用不绕过 Spring 拦截器。
     *
     * @author linshiqiang
     * @since 2026-10-06 03:28:05
     */
    public interface ListenerContract {

        /**
         * 处理一条消息。
         *
         * @param value 消息
         */
        void consume(Long value);
    }

    /**
     * 默认监听器(DefaultListener)从全局配置继承容量与线程设置。
     *
     * @author linshiqiang
     * @since 2026-10-06 03:28:05
     */
    public static class DefaultListener implements ListenerContract {

        /**
         * 实际消费线程类型。
         */
        private final AtomicReference<Boolean> virtual = new AtomicReference<>();

        /**
         * {@inheritDoc}
         */
        @Override
        @DisruptorListener("defaults")
        public void consume(Long value) {
            virtual.set(Thread.currentThread().isVirtual());
        }
    }
}
