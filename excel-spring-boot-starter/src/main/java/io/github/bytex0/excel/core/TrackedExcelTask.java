package io.github.bytex0.excel.core;

import org.springframework.util.Assert;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 可跟踪任务(TrackedExcelTask)区分 Future 已取消与业务调用真正退出，支持有限清理等待。
 *
 * @param <T> 任务结果类型
 * @author linshiqiang
 * @since 2026-10-06 11:06:59
 */
public final class TrackedExcelTask<T> extends FutureTask<T> {

    /**
     * 尚未进入执行器运行阶段。
     */
    private static final int NEW = 0;

    /**
     * 已进入运行阶段，取消后仍须等待 finally 退出。
     */
    private static final int RUNNING = 1;

    /**
     * 不会再执行或已经完全退出。
     */
    private static final int TERMINATED = 2;

    /**
     * 生命周期状态，防止排队取消与开始执行竞争。
     */
    private final AtomicInteger state = new AtomicInteger(NEW);

    /**
     * 真正退出信号，不把 Future.cancel 返回当作业务退出。
     */
    private final CountDownLatch terminated = new CountDownLatch(1);

    /**
     * 内部完成队列通知，不用于业务进度回调。
     */
    private final Consumer<TrackedExcelTask<T>> completion;

    /**
     * 构造可跟踪任务，不启动线程。
     *
     * @param callable 业务操作
     * @param completion 内部完成通知，必须非阻塞且不抛出异常
     */
    public TrackedExcelTask(Callable<T> callable, Consumer<TrackedExcelTask<T>> completion) {
        super(callable);
        Assert.notNull(completion, "任务完成通知不能为空");
        this.completion = completion;
    }

    /**
     * 执行至 finally 后才标记真正退出，预先取消的任务不会进入业务代码。
     */
    @Override
    public void run() {
        if (!state.compareAndSet(NEW, RUNNING)) {
            return;
        }
        try {
            super.run();
        } finally {
            state.set(TERMINATED);
            terminated.countDown();
        }
    }

    /**
     * 取消未启动任务时立即完成退出信号，运行中任务仍等待 finally。
     *
     * @param mayInterruptIfRunning 是否请求中断
     * @return Future 是否成功转为取消状态
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        if (cancelled && state.compareAndSet(NEW, TERMINATED)) {
            terminated.countDown();
        }
        return cancelled;
    }

    /**
     * 将 Future 完成事件交给内部队列，调用线程稍后统一执行进度回调。
     */
    @Override
    protected void done() {
        completion.accept(this);
    }

    /**
     * 检查业务调用是否已真正退出。
     *
     * @return 已退出时 true
     */
    public boolean isTerminated() {
        return terminated.getCount() == 0;
    }

    /**
     * 请求中断并有限等待实际退出；清理期间暂存当前线程中断，返回前恢复。
     *
     * @param timeout 非负等待上限
     * @return 是否在期限内退出，false 表示业务没有及时响应中断
     */
    public boolean cancelAndAwait(Duration timeout) {
        Assert.notNull(timeout, "取消等待时间不能为空");
        Assert.isTrue(!timeout.isNegative(), "取消等待不能为负数");
        cancel(true);
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean interrupted = Thread.interrupted();
        try {
            while (!isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    if (terminated.await(remaining, TimeUnit.NANOSECONDS)) {
                        return true;
                    }
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
