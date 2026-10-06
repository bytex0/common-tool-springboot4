package io.github.bytex0.common;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 可重置计数闩锁，使用完成代际避免归零后立即重置导致旧等待者再次阻塞。
 *
 * @author bytex0
 * @since 2026-10-06 14:50:52
 */
public class CountDownLatch2 {

    /**
     * 每次重置恢复的初始计数。
     */
    private final int initialCount;

    /**
     * 保护计数及完成代际的锁。
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 当前代际归零时发出信号。
     */
    private final Condition completed = lock.newCondition();

    /**
     * 当前计数，由 lock 保护。
     */
    private int count;

    /**
     * 每次归零递增的完成代际，由 lock 保护。
     */
    private long generation;

    /**
     * 创建可重置闩锁。
     *
     * @param count 初始计数，必须非负
     */
    public CountDownLatch2(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        initialCount = count;
        this.count = count;
    }

    /**
     * 等待调用时所在代际完成；已经归零时立即返回。
     *
     * @throws InterruptedException 等待或获取锁时被中断
     */
    public void await() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            long observed = generation;
            while (count > 0 && observed == generation) {
                completed.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 限时等待调用时所在代际完成。
     *
     * @param timeout 最大等待时长，非正值时不等待
     * @param unit 时间单位
     * @return 是否完成，超时为 false
     * @throws InterruptedException 等待或获取锁时被中断
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        long remaining = Objects.requireNonNull(unit, "unit").toNanos(timeout);
        lock.lockInterruptibly();
        try {
            long observed = generation;
            while (count > 0 && observed == generation) {
                if (remaining <= 0) {
                    return false;
                }
                remaining = completed.awaitNanos(remaining);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 计数减一，归零后释放本代等待者；已经归零时不再递减。
     */
    public void countDown() {
        lock.lock();
        try {
            if (count > 0 && --count == 0) {
                generation++;
                completed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前计数快照。
     *
     * @return 非负计数
     */
    public long getCount() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 恢复初始计数。尚未归零时重置会延长当前代等待，已完成代际的等待者不受影响。
     */
    public void reset() {
        lock.lock();
        try {
            count = initialCount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回当前计数的诊断文本。
     *
     * @return 闩锁状态
     */
    @Override
    public String toString() {
        return super.toString() + "[Count = " + getCount() + "]";
    }
}
