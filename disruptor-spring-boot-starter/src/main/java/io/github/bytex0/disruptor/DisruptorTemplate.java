package io.github.bytex0.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.Assert;

/**
 * 有界多生产者消息队列，消费确认、拒绝和停机行为对调用方可见。
 *
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
public class DisruptorTemplate implements SmartLifecycle, AutoCloseable {

    private final Map<String, Queue<?>> queues = new HashMap<>();

    private volatile boolean running;

    private boolean closed;

    /**
     * 环形缓冲区复用事件，消费后清除业务引用。
     *
     * @author bytex0
     * @since 2026-10-05 19:39:53
     */
    private static final class Event {

        private Object data;

        private CompletableFuture<Void> completion;
    }

    /**
     * 单队列生命周期与类型边界。
     *
     * @author bytex0
     * @since 2026-10-05 19:39:53
     */
    private static final class Queue<T> {

        private final MessageHandler<T> handler;

        private final Disruptor<Event> disruptor;

        private final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();

        private Thread worker;

        private boolean accepting;

        Queue(MessageHandler<T> handler, int size) {
            this.handler = handler;
            disruptor = new Disruptor<>(Event::new, size, task -> {
                worker = Thread.ofPlatform().daemon(true).name("disruptor-" + handler.name()).unstarted(task);
                return worker;
            }, ProducerType.MULTI, new BlockingWaitStrategy());
            disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
                CompletableFuture<Void> completion = event.completion;
                try {
                    handler.handle(handler.type().cast(event.data));
                    completion.complete(null);
                } catch (Throwable failure) {
                    completion.completeExceptionally(failure);
                } finally {
                    pending.remove(completion);
                    event.data = null;
                    event.completion = null;
                }
            });
        }

        synchronized void start() {
            disruptor.start();
            accepting = true;
        }

        synchronized CompletableFuture<Void> send(Object data) {
            if (!accepting) {
                throw new RejectedExecutionException("Queue is stopped");
            }
            Assert.isTrue(handler.type().isInstance(data), "Message has an incompatible type");
            CompletableFuture<Void> completion = new CompletableFuture<>();
            pending.add(completion);
            boolean accepted = disruptor.getRingBuffer().tryPublishEvent((event, sequence) -> {
                event.data = data;
                event.completion = completion;
            });
            if (!accepted) {
                pending.remove(completion);
                throw new RejectedExecutionException("Queue is full");
            }
            return completion;
        }

        void close() {
            synchronized (this) {
                accepting = false;
            }
            try {
                disruptor.shutdown(5, TimeUnit.SECONDS);
            } catch (TimeoutException timeout) {
                disruptor.halt();
            } finally {
                if (worker != null && worker != Thread.currentThread()) {
                    worker.interrupt();
                    try {
                        worker.join(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                pending.forEach(future -> future.completeExceptionally(
                        new RejectedExecutionException("Queue stopped before consumption completed")));
                pending.clear();
            }
        }
    }

    public DisruptorTemplate(List<MessageHandler<?>> handlers, int bufferSize) {
        Assert.isTrue(bufferSize >= 2 && bufferSize <= 1048576 && Integer.bitCount(bufferSize) == 1,
                "disruptor.buffer-size must be a power of two between 2 and 1048576");
        for (MessageHandler<?> handler : handlers) {
            Assert.hasText(handler.name(), "Queue name is required");
            Objects.requireNonNull(handler.type(), "Message type");
            Assert.isTrue(!queues.containsKey(handler.name()), "Duplicate queue name");
            queues.put(handler.name(), new Queue<>(handler, bufferSize));
        }
    }

    public CompletableFuture<Void> send(String queueName, Object data) {
        Queue<?> queue = queues.get(queueName);
        Assert.notNull(queue, "Unknown queue");
        return queue.send(Objects.requireNonNull(data));
    }

    public Set<String> names() {
        return Set.copyOf(queues.keySet());
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        Assert.state(!closed, "Template cannot be restarted after shutdown");
        try {
            queues.values().forEach(Queue::start);
            running = true;
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            running = false;
        }
        queues.values().forEach(Queue::close);
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
