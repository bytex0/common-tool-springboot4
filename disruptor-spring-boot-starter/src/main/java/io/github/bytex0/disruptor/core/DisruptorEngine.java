package io.github.bytex0.disruptor.core;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.github.bytex0.disruptor.MessageHandler;
import io.github.bytex0.disruptor.config.DisruptorProperties;
import io.github.bytex0.disruptor.event.DisruptorEvent;
import io.github.bytex0.disruptor.factory.DisruptorEventFactory;
import io.github.bytex0.disruptor.monitor.DisruptorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 队列引擎(DisruptorEngine)统一原注解/动态入口与类型化确认入口，注册和发布锁不等待业务回调。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
public class DisruptorEngine implements SmartLifecycle, AutoCloseable {

    /**
     * 支持的最大环容量。
     */
    private static final int MAX_BUFFER_SIZE = 1_048_576;

    /**
     * 单队列最大工作线程数。
     */
    private static final int MAX_WORKERS = 128;

    /**
     * 当前实例的队列，不与其他 Spring 容器共享。
     */
    private final Map<String, Queue<?>> queues = new ConcurrentHashMap<>();

    /**
     * 固定的默认配置，后续修改属性 Bean 不影响已创建队列。
     */
    private final DisruptorProperties properties;

    /**
     * 可选指标注册器。
     */
    private final DisruptorMetrics metrics;

    /**
     * 仅串行化注册和摘除，不启动线程或等待消费。
     */
    private final ReentrantLock registryLock = new ReentrantLock();

    /**
     * Spring 生命周期运行标记。
     */
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * 永久关闭标志，关闭后不能重新启动模板。
     */
    private volatile boolean closed;

    /**
     * 创建定义但不启动初始消费者，动态 createQueue 自己负责即时启动。
     *
     * @param handlers 当前类型化处理器
     * @param properties 全局默认值
     * @param metrics 可选监控
     */
    public DisruptorEngine(List<MessageHandler<?>> handlers, DisruptorProperties properties, DisruptorMetrics metrics) {
        this.properties = Objects.requireNonNull(properties).toBuilder().build();
        this.metrics = metrics;
        validate(this.properties);
        List<MessageHandler<?>> defined = List.copyOf(handlers);
        Assert.isTrue(defined.size() <= this.properties.getMaxQueues(), "队列数量超过上限");
        for (MessageHandler<?> handler : defined) {
            addInitial(handler);
        }
    }

    /**
     * 保留每个处理器的运行时类型，不通过未检查的消息强转放行错误数据。
     *
     * @param handler 处理器定义
     * @param <T> 消息类型
     */
    private <T> void addInitial(MessageHandler<T> handler) {
        Assert.hasText(handler.name(), "Queue name is required");
        Objects.requireNonNull(handler.type(), "Message type");
        Assert.isTrue(!queues.containsKey(handler.name()), "Duplicate queue name");
        Queue<T> queue = new Queue<>(handler.name(), handler.type(), properties.getBufferSize(),
                properties.getProducerType(), properties.getWaitStrategy().create(),
                threads(handler.name(), properties.isVirtualThread()), properties.getThreads(),
                (event, sequence, end) -> handler.handle(event.getData()), properties.getShutdownTimeout());
        queues.put(handler.name(), queue);
    }

    /**
     * 校验配置，在创建线程之前拒绝无界或无效选项。
     *
     * @param properties 配置
     */
    private static void validate(DisruptorProperties properties) {
        validateQueue(properties.getBufferSize(), properties.getThreads());
        Assert.notNull(properties.getProducerType(), "生产者类型不能为空");
        Assert.notNull(properties.getWaitStrategy(), "等待策略不能为空");
        Assert.isTrue(properties.getMaxQueues() > 0, "队列数上限必须大于零");
        Assert.isTrue(properties.getPublishTimeout() != null && !properties.getPublishTimeout().isNegative()
                && properties.getPublishTimeout().compareTo(Duration.ofMinutes(1)) <= 0, "发布等待必须在0至1分钟之间");
        Assert.isTrue(properties.getShutdownTimeout() != null && properties.getShutdownTimeout().toMillis() > 0
                && properties.getShutdownTimeout().compareTo(Duration.ofMinutes(1)) <= 0, "停止等待必须在1毫秒至1分钟之间");
    }

    /**
     * 校验实际队列容量和线程数。
     *
     * @param size 容量
     * @param workers 线程数
     */
    private static void validateQueue(int size, int workers) {
        Assert.isTrue(size >= 2 && size <= MAX_BUFFER_SIZE && Integer.bitCount(size) == 1,
                "disruptor.buffer-size must be a power of two between 2 and 1048576");
        Assert.isTrue(workers > 0 && workers <= MAX_WORKERS, "消费线程数必须在1至128之间");
    }

    /**
     * 创建带名称的虚拟或平台线程工厂。
     *
     * @param name 队列名
     * @param virtual 是否虚拟线程
     * @return 非阻塞线程工厂
     */
    public static ThreadFactory threads(String name, boolean virtual) {
        return virtual ? Thread.ofVirtual().name("disruptor-" + name + "-", 0).factory()
                : Thread.ofPlatform().daemon(true).name("disruptor-" + name + "-", 0).factory();
    }

    /**
     * 创建、启动并注册托管队列，重复名称立即失败且关闭本次创建的线程。
     *
     * @param name 队列名称
     * @param size 环容量
     * @param producer 生产者模式
     * @param wait 等待策略
     * @param factory 线程工厂，必须快速返回未启动线程
     * @param workers 工作线程数
     * @param type 运行时消息类型
     * @param handler 同步处理器
     * @param <T> 消息类型
     * @return 已启动的 LMAX 队列
     */
    public <T> Disruptor<DisruptorEvent<T>> createQueue(String name, int size, ProducerType producer, WaitStrategy wait,
                                                      ThreadFactory factory, int workers, Class<T> type,
                                                      EventHandler<DisruptorEvent<T>> handler) {
        validateQueue(size, workers);
        Assert.hasText(name, "队列名称不能为空");
        Assert.state(!closed, "模板已关闭");
        Assert.isTrue(!queues.containsKey(name), "Duplicate queue name");
        Queue<T> queue = new Queue<>(name, type, size, producer, wait, factory, workers, handler, properties.getShutdownTimeout());
        try {
            queue.start();
            install(name, queue);
            return queue.disruptor;
        } catch (RuntimeException | Error failure) {
            queue.close();
            throw failure;
        }
    }

    /**
     * 登记调用方已经启动的原生队列，成功登记后模板负责停止；不改写用户消费链。
     *
     * @param name 队列名
     * @param disruptor 已配置并启动的原生队列
     */
    public void registerDisruptor(String name, Disruptor<DisruptorEvent<Object>> disruptor) {
        install(name, new Queue<>(name, Objects.requireNonNull(disruptor), properties.getShutdownTimeout()));
    }

    /**
     * 原子登记名称，然后注册指标；若并发关闭已摘除实例，撤销迟到的指标注册。
     *
     * @param name 名称
     * @param queue 队列
     */
    private void install(String name, Queue<?> queue) {
        Assert.hasText(name, "队列名称不能为空");
        registryLock.lock();
        try {
            Assert.state(!closed, "模板已关闭");
            Assert.isTrue(!queues.containsKey(name), "Duplicate queue name");
            Assert.state(queues.size() < properties.getMaxQueues(), "队列数量超过上限");
            queues.put(name, queue);
        } finally {
            registryLock.unlock();
        }
        try {
            registerMetrics(name, queue.disruptor);
            if (queues.get(name) != queue && metrics != null) {
                metrics.unregister(name, queue.disruptor);
            }
        } catch (RuntimeException exception) {
            queues.remove(name, queue);
            queue.close();
            throw exception;
        }
    }

    /**
     * 按需注册原监控指标。
     *
     * @param name 队列名
     * @param disruptor 当前实例
     */
    public void registerMetrics(String name, Disruptor<?> disruptor) {
        if (metrics != null) {
            metrics.register(name, disruptor);
        }
    }

    /**
     * 立即尝试发布并返回实际消费确认，满队列拒绝，不在发布锁内等待。
     *
     * @param name 队列名
     * @param data 非空且符合队列类型的数据
     * @return 消费成功或失败确认
     */
    public CompletableFuture<Void> send(String name, Object data) {
        Queue<?> queue = queue(name);
        Assert.isTrue(queue.managed, "外部原生队列不提供消费确认，请使用原send入口");
        CompletableFuture<Void> completion = queue.offer(Objects.requireNonNull(data), true);
        if (completion == null) {
            throw new RejectedExecutionException("Queue is full");
        }
        return completion;
    }

    /**
     * 原 void 发送入口，满队列最多等待配置时长；消费者自发消息满载时立即拒绝以避免死锁。
     *
     * @param name 队列名
     * @param data 消息，原接口允许 null
     */
    public void sendLegacy(String name, Object data) {
        Queue<?> queue = queue(name);
        long started = System.nanoTime();
        long budget = properties.getPublishTimeout().toNanos();
        while (queue.offer(data, false) == null) {
            if (queue.workers.contains(Thread.currentThread()) || System.nanoTime() - started >= budget) {
                throw new RejectedExecutionException("Queue is full");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new RejectedExecutionException("发布等待被中断");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    /**
     * 获取可接受调用的队列。
     *
     * @param name 名称
     * @return 队列
     */
    private Queue<?> queue(String name) {
        if (closed) {
            throw new RejectedExecutionException("Template is closed");
        }
        Assert.hasText(name, "队列名称不能为空");
        Queue<?> queue = queues.get(name);
        Assert.notNull(queue, "Unknown queue");
        return queue;
    }

    /**
     * 获取只读名称快照。
     *
     * @return 队列名集合
     */
    public Set<String> names() {
        return Set.copyOf(queues.keySet());
    }

    /**
     * 获取普通数值统计，不暴露消息数据或线程对象。
     *
     * @param name 名称
     * @return 当前统计快照
     */
    public Map<String, Object> stats(String name) {
        Queue<?> queue = queue(name);
        return Map.of("capacity", queue.disruptor.getRingBuffer().getBufferSize(),
                "remaining", queue.disruptor.getRingBuffer().remainingCapacity(), "published", queue.published.get(),
                "consumed", queue.consumed.get(), "failed", queue.failed.get(), "threads", queue.workers.size(),
                "accepting", queue.accepting, "managed", queue.managed);
    }

    /**
     * 停止一个队列并删除指标，允许之后使用同名新队列。
     *
     * @param name 名称
     */
    public void shutdown(String name) {
        Queue<?> queue = queues.remove(name);
        if (queue != null) {
            closeQueue(name, queue);
        }
    }

    /**
     * 关闭当前全部队列但保留模板注册能力，对齐原 shutdownAll 行为。
     */
    public void shutdownAll() {
        Map<String, Queue<?>> closing;
        registryLock.lock();
        try {
            closing = Map.copyOf(queues);
            queues.clear();
        } finally {
            registryLock.unlock();
        }
        closing.forEach(this::closeQueue);
    }

    /**
     * 不持注册锁等待队列退出，指标按实例移除。
     *
     * @param name 名称
     * @param queue 原队列
     */
    private void closeQueue(String name, Queue<?> queue) {
        try {
            queue.close();
        } finally {
            if (metrics != null) {
                metrics.unregister(name, queue.disruptor);
            }
        }
    }

    /**
     * 启动初始定义的队列，动态队列已启动时不重复启动。
     */
    @Override
    public void start() {
        Assert.state(!closed, "Template cannot be restarted after shutdown");
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            queues.forEach((name, queue) -> {
                queue.start();
                if (queues.get(name) == queue) {
                    registerMetrics(name, queue.disruptor);
                    if (queues.get(name) != queue && metrics != null) {
                        metrics.unregister(name, queue.disruptor);
                    }
                }
            });
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /**
     * 永久关闭模板，不持注册锁等待用户回调。
     */
    @Override
    public void close() {
        registryLock.lock();
        try {
            closed = true;
            running.set(false);
        } finally {
            registryLock.unlock();
        }
        shutdownAll();
    }

    /**
     * Spring 停机采用永久关闭语义。
     */
    @Override
    public void stop() {
        close();
    }

    /**
     * 获取 Spring 生命周期状态。
     *
     * @return 是否已启动且未关闭
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 单队列(Queue)持有有限容量、发布边界和消费线程，不使用内置监视器。
     *
     * @author linshiqiang
     * @since 2026-10-06 03:17:49
     * @param <T> 消息类型
     */
    private static final class Queue<T> {

        /**
         * 不输出消息数据的错误日志。
         */
        private static final Logger LOG = LoggerFactory.getLogger(Queue.class);

        /**
         * 当前名称。
         */
        private final String name;

        /**
         * 消息类型。
         */
        private final Class<T> type;

        /**
         * 实际 LMAX 队列。
         */
        private final Disruptor<DisruptorEvent<T>> disruptor;

        /**
         * 是否由当前引擎包装消费，因此能够确认完成。
         */
        private final boolean managed;

        /**
         * 引擎实际创建的工作线程，外部原生队列不在此集合。
         */
        private final Set<Thread> workers = ConcurrentHashMap.newKeySet();

        /**
         * 尚未完成的有限确认集合。
         */
        private final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();

        /**
         * 序列化发布与停止接收，SINGLE 生产者也可被多个线程安全调用。
         */
        private final ReentrantLock publication = new ReentrantLock();

        /**
         * 是否已开始启动。
         */
        private final AtomicBoolean started = new AtomicBoolean();

        /**
         * 启动是否已结束，关闭与启动竞争时由启动方补充清理。
         */
        private volatile boolean startupComplete;

        /**
         * 是否接受发布。
         */
        private volatile boolean accepting;

        /**
         * 已关闭标志。
         */
        private volatile boolean closed;

        /**
         * 单队列优雅停止上限。
         */
        private final Duration shutdownTimeout;

        /**
         * 成功发布数。
         */
        private final AtomicLong published = new AtomicLong();

        /**
         * 成功消费数。
         */
        private final AtomicLong consumed = new AtomicLong();

        /**
         * 消费失败数。
         */
        private final AtomicLong failed = new AtomicLong();

        /**
         * 创建工作线程划分，每条消息只由序号指定的一个工作线程消费。
         *
         * @param name 队列名
         * @param type 消息类型
         * @param size 容量
         * @param producer 生产者类型
         * @param wait 等待策略
         * @param factory 线程工厂
         * @param workerCount 消费线程数
         * @param handler 用户处理器
         * @param shutdownTimeout 关闭等待
         */
        @SuppressWarnings("unchecked")
        private Queue(String name, Class<T> type, int size, ProducerType producer, WaitStrategy wait, ThreadFactory factory,
                      int workerCount, EventHandler<DisruptorEvent<T>> handler, Duration shutdownTimeout) {
            Assert.hasText(name, "队列名称不能为空");
            this.name = name;
            this.type = Objects.requireNonNull(type);
            this.shutdownTimeout = shutdownTimeout;
            this.managed = true;
            Objects.requireNonNull(handler);
            Objects.requireNonNull(factory);
            disruptor = new Disruptor<>(new DisruptorEventFactory<>(), size, task -> {
                Thread worker = Objects.requireNonNull(factory.newThread(task), "线程工厂返回null");
                Assert.isTrue(worker.getState() == Thread.State.NEW, "线程工厂必须返回未启动线程");
                workers.add(worker);
                return worker;
            }, Objects.requireNonNull(producer), Objects.requireNonNull(wait));
            EventHandler<DisruptorEvent<T>>[] handlers = new EventHandler[workerCount];
            for (int index = 0; index < workerCount; index++) {
                int lane = index;
                handlers[index] = (event, sequence, end) -> {
                    if (sequence % workerCount == lane) {
                        consume(event, sequence, end, handler);
                    }
                };
            }
            disruptor.handleEventsWith(handlers);
            disruptor.setDefaultExceptionHandler(new ExceptionHandler<>() {

                /**
                 * LMAX 捕获致命错误后的边界处理，停止队列并使确认失败。
                 *
                 * @param failure 原错误
                 * @param sequence 当前序号
                 * @param event 当前事件
                 */
                @Override
                public void handleEventException(Throwable failure, long sequence, DisruptorEvent<T> event) {
                    failQueue(failure);
                }

                /**
                 * 启动钩子失败时关闭接收。
                 *
                 * @param failure 原错误
                 */
                @Override
                public void handleOnStartException(Throwable failure) {
                    failQueue(failure);
                }

                /**
                 * 关闭钩子失败保留在待确认结果中。
                 *
                 * @param failure 原错误
                 */
                @Override
                public void handleOnShutdownException(Throwable failure) {
                    failQueue(failure);
                }
            });
        }

        /**
         * 包装调用方已启动的原生队列，不修改其处理器链或线程工厂。
         *
         * @param name 名称
         * @param disruptor 原生实例
         * @param timeout 停止上限
         */
        @SuppressWarnings("unchecked")
        private Queue(String name, Disruptor<DisruptorEvent<T>> disruptor, Duration timeout) {
            this.name = name;
            this.type = (Class<T>) Object.class;
            this.disruptor = disruptor;
            this.shutdownTimeout = timeout;
            managed = false;
            started.set(true);
            startupComplete = true;
            accepting = true;
        }

        /**
         * 执行用户消费，普通失败不停止队列，致命 Error 交由 LMAX 异常边界关闭。
         *
         * @param event 可复用事件
         * @param sequence 序号
         * @param end 批次末尾
         * @param handler 用户处理器
         */
        private void consume(DisruptorEvent<T> event, long sequence, boolean end, EventHandler<DisruptorEvent<T>> handler) {
            CompletableFuture<Void> completion = event.completion();
            try {
                handler.onEvent(event, sequence, end);
                consumed.incrementAndGet();
                if (completion != null) {
                    completion.complete(null);
                }
            } catch (Exception failure) {
                failed.incrementAndGet();
                if (completion != null) {
                    completion.completeExceptionally(failure);
                }
                LOG.error("Disruptor handler failed, queue={}, exceptionType={}", name, failure.getClass().getName());
            } catch (Error failure) {
                failed.incrementAndGet();
                if (completion != null) {
                    completion.completeExceptionally(failure);
                }
                throw failure;
            } finally {
                event.clear();
                if (completion != null) {
                    pending.remove(completion);
                }
            }
        }

        /**
         * 启动时不持发布锁调用外部线程工厂；并发关闭由启动完成路径负责补充停止。
         */
        private void start() {
            if (closed || !started.compareAndSet(false, true)) {
                return;
            }
            try {
                disruptor.start();
                publication.lock();
                try {
                    accepting = !closed;
                } finally {
                    publication.unlock();
                }
            } catch (RuntimeException | Error failure) {
                closed = true;
                throw failure;
            } finally {
                startupComplete = true;
                if (closed) {
                    haltAndFail(new RejectedExecutionException("Queue stopped during startup"));
                }
            }
        }

        /**
         * 一次无等待发布，只有成功写入环才保留确认。
         *
         * @param data 数据
         * @param requireAck 是否要求托管消费确认
         * @return 确认，满载时为 null
         */
        private CompletableFuture<Void> offer(Object data, boolean requireAck) {
            publication.lock();
            try {
                if (!accepting || closed) {
                    throw new RejectedExecutionException("Queue is stopped");
                }
                Assert.isTrue(data == null || type.isInstance(data), "Message has an incompatible type");
                Assert.isTrue(!requireAck || managed, "原生外部队列不提供消费确认");
                CompletableFuture<Void> completion = new CompletableFuture<>();
                if (managed) {
                    pending.add(completion);
                }
                boolean accepted = disruptor.getRingBuffer().tryPublishEvent((event, sequence) -> {
                    event.setData(type.cast(data));
                    event.bindCompletion(managed ? completion : null);
                });
                if (!accepted) {
                    pending.remove(completion);
                    return null;
                }
                published.incrementAndGet();
                if (!managed) {
                    completion.complete(null);
                }
                return completion;
            } finally {
                publication.unlock();
            }
        }

        /**
         * LMAX 异常回调只停止接收，不等待当前消费线程自身。
         *
         * @param failure 原错误
         */
        private void failQueue(Throwable failure) {
            publication.lock();
            try {
                closed = true;
                accepting = false;
            } finally {
                publication.unlock();
            }
            haltAndFail(failure);
        }

        /**
         * 停止环并结束尚未完成的确认，不输出消息内容。
         *
         * @param cause 终止原因
         */
        private void haltAndFail(Throwable cause) {
            disruptor.halt();
            workers.forEach(worker -> {
                if (worker != Thread.currentThread()) {
                    worker.interrupt();
                }
            });
            pending.forEach(future -> future.completeExceptionally(cause));
            pending.clear();
        }

        /**
         * 先禁止发布再等待消费，消费者自关闭不能等待自己；普通关闭额外最多等待一秒线程退出。
         */
        private void close() {
            publication.lock();
            try {
                if (closed) {
                    return;
                }
                closed = true;
                accepting = false;
            } finally {
                publication.unlock();
            }
            if (!startupComplete) {
                return;
            }
            if (workers.contains(Thread.currentThread())) {
                haltAndFail(new RejectedExecutionException("Queue closed by consumer"));
                return;
            }
            try {
                disruptor.shutdown(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                LOG.warn("Disruptor shutdown timed out, queue={}", name);
            } finally {
                haltAndFail(new RejectedExecutionException("Queue stopped before consumption completed"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                for (Thread worker : new ArrayList<>(workers)) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        worker.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
}
