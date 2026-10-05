package io.github.bytex0.threadpool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

/**
 * 管理命名线程池及有序扩缩容，不暴露可变 Executor 给调用方。
 *
 * @author bytex0
 * @since 2026-10-05 20:05:22
 */
public class ThreadPoolRegistry implements AutoCloseable {

    private final Map<String, ThreadPoolTaskExecutor> pools = new HashMap<>();

    private final Map<String, LongAdder> rejections = new HashMap<>();

    private final ReentrantLock configurationLock = new ReentrantLock();

    private boolean closed;

    /**
     * 实时运行快照。
     *
     * @param core 核心线程数
     * @param max 最大线程数
     * @param capacity 固定队列容量
     * @param active 正在执行数
     * @param queued 等待任务数
     * @param completed 已完成任务数
     * @param rejected 被拒绝任务数
     * @author bytex0
     * @since 2026-10-05 20:05:22
     */
    public record Stats(int core, int max, int capacity, int active, int queued, long completed, long rejected) {}

    public ThreadPoolRegistry(ThreadPoolProperties properties, TaskDecorator decorator) {
        properties.pools().keySet().forEach(name ->
                Assert.isTrue(name.matches("[a-zA-Z0-9_-]{1,64}"), "Invalid pool name"));
        try {
            properties.pools().forEach((name, settings) -> {
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                LongAdder rejected = new LongAdder();
                executor.setCorePoolSize(settings.core());
                executor.setMaxPoolSize(settings.max());
                executor.setQueueCapacity(settings.capacity());
                executor.setThreadNamePrefix("dynamic-" + name + "-");
                executor.setDaemon(true);
                executor.setAwaitTerminationSeconds(5);
                executor.setTaskDecorator(decorator);
                executor.setRejectedExecutionHandler((task, pool) -> {
                    rejected.increment();
                    throw new RejectedExecutionException("Thread pool rejected task");
                });
                executor.initialize();
                pools.put(name, executor);
                rejections.put(name, rejected);
            });
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    public <T> Future<T> submit(String name, Callable<T> task) {
        ThreadPoolTaskExecutor executor;
        configurationLock.lock();
        try {
            if (closed) {
                throw new RejectedExecutionException("Thread pool registry is closed");
            }
            executor = get(name);
        } finally {
            configurationLock.unlock();
        }
        return executor.submit(task);
    }

    public Stats resize(String name, int core, int max) {
        configurationLock.lock();
        try {
            Assert.state(!closed, "Pool is stopped");
            ThreadPoolTaskExecutor executor = get(name);
            new ThreadPoolProperties.Settings(core, max, executor.getQueueCapacity());
            if (max > executor.getMaxPoolSize()) {
                executor.setMaxPoolSize(max);
            }
            executor.setCorePoolSize(core);
            executor.setMaxPoolSize(max);
            return snapshot(name);
        } finally {
            configurationLock.unlock();
        }
    }

    public Stats stats(String name) {
        configurationLock.lock();
        try {
            return snapshot(name);
        } finally {
            configurationLock.unlock();
        }
    }

    private Stats snapshot(String name) {
        ThreadPoolTaskExecutor executor = get(name);
        return new Stats(executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(),
                executor.getActiveCount(), executor.getQueueSize(),
                executor.getThreadPoolExecutor().getCompletedTaskCount(), rejections.get(name).sum());
    }

    private ThreadPoolTaskExecutor get(String name) {
        ThreadPoolTaskExecutor executor = pools.get(name);
        Assert.notNull(executor, "Unknown thread pool");
        return executor;
    }

    @Override
    public void close() {
        configurationLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
        } finally {
            configurationLock.unlock();
        }
        pools.values().forEach(ThreadPoolTaskExecutor::shutdown);
    }
}
