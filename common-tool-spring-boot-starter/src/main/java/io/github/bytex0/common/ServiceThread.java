package io.github.bytex0.common;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 可唤醒、可停止并可在旧线程退出后重启的后台服务线程。
 * 子类 run 必须检查 isStopped，不能假设停机能够强制终止任意业务代码。
 *
 * @author bytex0
 * @since 2026-10-06 14:50:52
 */
public abstract class ServiceThread implements Runnable {

    /**
     * 默认非守护线程停机等待上限，单位毫秒。
     */
    private static final long JOIN_TIME = 90000;

    /**
     * 保留原可重置等待点，wakeup 和停机仍会释放它。
     */
    protected final CountDownLatch2 waitPoint = new CountDownLatch2(1);

    /**
     * 是否存在尚未消费的唤醒信号，保留子类扩展字段。
     */
    protected volatile AtomicBoolean hasNotified = new AtomicBoolean();

    /**
     * 停机标记，子类循环应读取此标记。
     */
    protected volatile boolean stopped;

    /**
     * 下一次启动是否创建守护线程，默认 false。
     */
    protected volatile boolean isDaemon;

    /**
     * 保护启动与实际线程退出的生命周期状态，不在持锁时等待线程退出。
     */
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    /**
     * 保护唤醒信号与条件等待。
     */
    private final ReentrantLock notificationLock = new ReentrantLock();

    /**
     * 唤醒条件，允许信号在等待前到达。
     */
    private final Condition notification = notificationLock.newCondition();

    /**
     * 当前实际线程，由 lifecycleLock 保护。
     */
    private Thread thread;

    /**
     * 是否处于实际运行生命周期，线程退出后才清除。
     */
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * 创建尚未启动的后台服务。
     */
    public ServiceThread() {
    }

    /**
     * 获取线程名称，不应有阻塞副作用。
     *
     * @return 非空服务名称
     */
    public abstract String getServiceName();

    /**
     * 幂等启动。旧线程未退出时不创建第二个线程，也不清除其停机标记。
     */
    public void start() {
        String name = getServiceName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Service name is required");
        }
        lifecycleLock.lock();
        try {
            if (started.get() || (thread != null && thread.isAlive())) {
                return;
            }
            stopped = false;
            thread = new Thread(this::runService, name);
            thread.setDaemon(isDaemon);
            started.set(true);
            try {
                thread.start();
            } catch (RuntimeException | Error exception) {
                started.set(false);
                stopped = true;
                thread = null;
                throw exception;
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * 执行业务循环，正常或异常退出都允许后续重新启动。
     */
    private void runService() {
        try {
            run();
        } finally {
            lifecycleLock.lock();
            try {
                stopped = true;
                started.set(false);
            } finally {
                lifecycleLock.unlock();
            }
        }
    }

    /**
     * 请求停机并唤醒等待，不额外发送中断。
     */
    public void shutdown() {
        shutdown(false);
    }

    /**
     * 请求停机，非守护线程最多等待 getJointime 毫秒，自身停机不 join 自身。
     *
     * @param interrupt 是否同时中断业务线程
     */
    public void shutdown(boolean interrupt) {
        Thread target = requestStop(interrupt, true);
        if (target == null || target == Thread.currentThread() || target.isDaemon()) {
            return;
        }
        long joinTime = getJointime();
        if (joinTime <= 0) {
            throw new IllegalStateException("Join timeout must be positive");
        }
        try {
            target.join(joinTime);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取停机等待上限，保留原方法拼写。
     *
     * @return 正数毫秒值，默认 90000
     */
    public long getJointime() {
        return JOIN_TIME;
    }

    /**
     * 请求停机但不等待，保留原弃用入口。
     */
    @Deprecated
    public void stop() {
        stop(false);
    }

    /**
     * 请求停机并唤醒，但不等待业务线程结束。
     *
     * @param interrupt 是否额外中断业务线程
     */
    @Deprecated
    public void stop(boolean interrupt) {
        requestStop(interrupt, true);
    }

    /**
     * 只设置停机标记，不唤醒或等待，保留原调用语义。
     */
    public void makeStop() {
        requestStop(false, false);
    }

    /**
     * 标记停机并按需通知，不持生命周期锁调用唤醒或中断。
     *
     * @param interrupt 是否中断
     * @param notify 是否唤醒
     * @return 目标线程，未启动时为空
     */
    private Thread requestStop(boolean interrupt, boolean notify) {
        Thread target;
        lifecycleLock.lock();
        try {
            if (!started.get()) {
                return null;
            }
            stopped = true;
            target = thread;
        } finally {
            lifecycleLock.unlock();
        }
        if (notify) {
            wakeup();
        }
        if (interrupt) {
            target.interrupt();
        }
        return target;
    }

    /**
     * 记录一次可合并的唤醒信号，等待前到达的信号会被下一次等待消费。
     */
    public void wakeup() {
        notificationLock.lock();
        try {
            hasNotified.set(true);
            waitPoint.countDown();
            notification.signalAll();
        } finally {
            notificationLock.unlock();
        }
    }

    /**
     * 等待唤醒、停机或超时。被中断时保留中断标志并标记服务停止。
     *
     * @param interval 最大等待毫秒，非正数时不等待
     */
    protected void waitForRunning(long interval) {
        try {
            notificationLock.lockInterruptibly();
            try {
                long remaining = TimeUnit.MILLISECONDS.toNanos(interval);
                waitPoint.reset();
                while (!hasNotified.get() && !stopped && remaining > 0) {
                    remaining = notification.awaitNanos(remaining);
                }
                hasNotified.set(false);
            } finally {
                notificationLock.unlock();
            }
        } catch (InterruptedException exception) {
            stopped = true;
            Thread.currentThread().interrupt();
        } finally {
            onWaitEnd();
        }
    }

    /**
     * 每次等待结束后的子类扩展回调，默认不执行动作，调用时不持有内部锁。
     */
    protected void onWaitEnd() {
    }

    /**
     * 获取停机标记。
     *
     * @return 是否已请求停止或已退出
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * 获取下次启动的守护线程配置。
     *
     * @return 是否使用守护线程
     */
    public boolean isDaemon() {
        return isDaemon;
    }

    /**
     * 设置下次启动的守护线程属性，不修改已经运行的线程。
     *
     * @param daemon 是否使用守护线程
     */
    public void setDaemon(boolean daemon) {
        isDaemon = daemon;
    }
}
