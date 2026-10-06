package io.github.bytex0.redis;

import com.alibaba.ttl.TtlRunnable;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 双写协调(RedisWriteCoordinator)对主写与备写入队建立同一顺序，并限制待复制任务数。
 * 为保持主库顺序与复制顺序一致，主库命令在可限时获取的锁内执行；
 * 命令本身受 Redisson 的响应和重试超时约束。等待 Future 和关闭不持此锁。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:00:40
 */
final class RedisWriteCoordinator implements AutoCloseable {

    /**
     * 只记录失败数量与异常类型，不记录键、数据、连接或异常消息。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisWriteCoordinator.class);

    /**
     * 只借用、不关闭的主客户端。
     */
    private final RedissonClient primary;

    /**
     * 可选的异步备客户端。
     */
    private final RedissonClient backup;

    /**
     * 容器或调用方管理的任务执行器。
     */
    private final ExecutorService executor;

    /**
     * 主写序列化锁，不用于读取操作。
     */
    private final ReentrantLock order = new ReentrantLock();

    /**
     * 待复制任务的容量，包含当前正在执行的备库任务。
     */
    private final Semaphore slots;

    /**
     * 排队与关闭的等待上限。
     */
    private final Duration timeout;

    /**
     * 有界记录首次备库失败，避免无限保留异常对象。
     */
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    /**
     * 累计备库执行或调度失败数量。
     */
    private final AtomicLong failures = new AtomicLong();

    /**
     * 锁内更新的异步任务尾部；后续任务在前一任务结束后才开始执行。
     */
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    /**
     * 锁内读写的停止接收状态。
     */
    private boolean closed;

    /**
     * 创建有限队列的双写协调器，客户端和执行器所有权不转移。
     *
     * @param primary 主客户端
     * @param backup 备客户端，可为 null
     * @param executor 有备库时不能为空
     * @param capacity 待复制任务容量，必须大于 0
     * @param timeout 等待上限，必须大于 0
     */
    RedisWriteCoordinator(RedissonClient primary, RedissonClient backup, ExecutorService executor,
                          int capacity, Duration timeout) {
        Assert.notNull(primary, "主Redis客户端不能为空");
        Assert.isTrue(backup == null || backup != primary, "双写不能指向同一个客户端实例");
        Assert.isTrue(backup == null || executor != null, "双写需要执行器");
        Assert.isTrue(capacity > 0, "复制队列容量必须大于0");
        positive(timeout);
        this.primary = primary;
        this.backup = backup;
        this.executor = executor;
        this.slots = new Semaphore(capacity);
        this.timeout = timeout;
    }

    /**
     * 执行主操作并按顺序异步复制；主库失败直接抛出，不触发备库。
     *
     * @param action 主操作
     * @param mirror 使用主操作结果的备操作；null 表示只在主库执行
     * @param <T> 主操作结果类型
     * @return 主操作结果
     */
    <T> T write(Function<RedissonClient, T> action, BiConsumer<RedissonClient, T> mirror) {
        acquire();
        boolean reserved = false;
        boolean replicate = backup != null && mirror != null;
        try {
            Assert.state(!closed, "Redis工具已关闭");
            if (replicate) {
                if (!slots.tryAcquire()) {
                    throw new RejectedExecutionException("Redis复制队列已满，主操作尚未执行");
                }
                reserved = true;
            }
            T result = action.apply(primary);
            if (replicate) {
                Runnable task = TtlRunnable.get(() -> mirror.accept(backup, result));
                tail = tail.handle((ignored, previousFailure) -> null)
                        .thenRunAsync(task, executor)
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                                failure.compareAndSet(null, cause);
                                long count = failures.incrementAndGet();
                                LOGGER.warn("Redis备写失败，累计次数={}，异常类型={}", count, cause.getClass().getSimpleName());
                            }
                            slots.release();
                        });
                reserved = false;
            }
            return result;
        } finally {
            if (reserved) {
                slots.release();
            }
            order.unlock();
        }
    }

    /**
     * 等待调用前已经接收的复制任务；历史失败持续可见，不自动清零。
     *
     * @param wait 等待上限
     */
    void await(Duration wait) {
        positive(wait);
        CompletableFuture<Void> pending;
        acquire();
        try {
            pending = tail;
        } finally {
            order.unlock();
        }
        drain(pending, wait);
        if (failure.get() != null) {
            throw new IllegalStateException("Redis备写存在失败，主库可能已成功", failure.get());
        }
    }

    /**
     * 获取累计失败数量。
     *
     * @return 备写失败数量
     */
    long failures() {
        return failures.get();
    }

    /**
     * 停止接收并限时等待已接收复制，不关闭借用的执行器和客户端。
     */
    @Override
    public void close() {
        CompletableFuture<Void> pending;
        acquire();
        try {
            closed = true;
            pending = tail;
        } finally {
            order.unlock();
        }
        drain(pending, timeout);
    }

    /**
     * 可中断限时获取主写顺序锁。
     */
    private void acquire() {
        try {
            if (!order.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new RejectedExecutionException("Redis主写排队超时，操作尚未执行");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待Redis主写被中断", exception);
        }
    }

    /**
     * 在锁外等待指定尾部，具体备库失败由累计状态报告。
     *
     * @param pending 指定时刻的尾部
     * @param wait 等待上限
     */
    private void drain(CompletableFuture<Void> pending, Duration wait) {
        try {
            pending.handle((ignored, error) -> null).get(wait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待Redis复制被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("等待Redis复制失败或超时", exception);
        }
    }

    /**
     * 检查有效的有限等待时长。
     *
     * @param duration 时长
     */
    private static void positive(Duration duration) {
        Assert.notNull(duration, "等待时间不能为空");
        Assert.isTrue(!duration.isNegative() && !duration.isZero(), "等待时间必须大于0");
        duration.toNanos();
    }
}
