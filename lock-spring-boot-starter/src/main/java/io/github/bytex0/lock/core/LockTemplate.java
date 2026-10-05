package io.github.bytex0.lock.core;

import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.model.LockRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import io.github.bytex0.util.ThrowingSupplier;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 锁执行(LockTemplate)以作用域保证成功获取后才释放
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
public class LockTemplate implements AutoCloseable {

    /**
     * 仅在Redis策略使用时获取客户端
     */
    private final Supplier<RedissonClient> redis;

    /**
     * 本地活动键及等待者，最后一个引用离开后移除
     */
    private final Map<String, LocalSlot> local = new ConcurrentHashMap<>();

    /**
     * 信号量租约续期线程，首次使用才启动
     */
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("common-tool-lock-renewal").factory());

    /**
     * 模板是否已关闭
     */
    private volatile boolean closed;

    public LockTemplate(Supplier<RedissonClient> redis) { this.redis = redis; }

    public <T> T execute(LockRule rule, ThrowingSupplier<T> action) throws Throwable {
        Assert.state(!closed, "锁模板已关闭");
        Assert.notNull(rule, "锁规则不能为空");
        if (!rule.isEnable()) { return action.get(); }
        Assert.hasText(rule.getKey(), "锁key不能为空");
        Assert.notNull(rule.getLockType(), "锁类型不能为空");
        Assert.notNull(rule.getTimeUnit(), "时间单位不能为空");
        Assert.isTrue(rule.getPermits() > 0 && rule.getTimeout() >= 0 && rule.getLeaseTime() >= 0, "锁参数不合法");
        try (Guard guard = acquire(rule)) {
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    public int activeLocalKeys() { return local.size(); }

    private Guard acquire(LockRule rule) throws InterruptedException {
        LockType type = rule.getLockType();
        long wait = rule.isBlock() ? rule.getTimeUnit().toMillis(rule.getTimeout()) : 0;
        long lease = rule.getTimeUnit().toMillis(rule.getLeaseTime());
        String group = switch (type) {
            case REDISSON_READ_LOCK, REDISSON_WRITE_LOCK, REDISSON_READ_WRITE_LOCK -> "RW";
            case REDISSON_SEMAPHORE, REDIS_TEMPLATE_SEMAPHORE -> "SEMAPHORE";
            default -> type.name();
        };
        String key = "common-tool:lock:" + group + ":" + MethodExpressionEvaluator.digest(rule.getKey());
        if (type == LockType.REENTRANT_LOCK || type == LockType.SEMAPHORE) {
            LocalSlot slot = local.compute(key, (ignored, previous) -> {
                LocalSlot next = previous == null ? new LocalSlot(rule) : previous;
                Assert.isTrue(next.permits == rule.getPermits() && next.fair == rule.isFair(), "同一个锁键的配置不一致");
                next.references++;
                return next;
            });
            boolean acquired = false;
            try {
                acquired = type == LockType.SEMAPHORE ? slot.semaphore.tryAcquire(wait, TimeUnit.MILLISECONDS)
                        : slot.lock.tryLock(wait, TimeUnit.MILLISECONDS);
                if (!acquired) { throw new LockException("未获取锁"); }
                return guard(() -> {
                    try {
                        if (type == LockType.SEMAPHORE) { slot.semaphore.release(); } else { slot.lock.unlock(); }
                    } finally { releaseReference(key); }
                });
            } finally {
                if (!acquired) { releaseReference(key); }
            }
        }
        RedissonClient client = redis.get();
        Assert.state(client != null, "Redis锁策略需要RedissonClient");
        if (type == LockType.REDISSON_SEMAPHORE || type == LockType.REDIS_TEMPLATE_SEMAPHORE) {
            return semaphore(client.getPermitExpirableSemaphore(key), rule.getPermits(), wait, lease == 0 ? 30000 : lease);
        }
        RLock lock = switch (type) {
            case REDISSON_FAIR_LOCK -> client.getFairLock(key);
            case REDISSON_SPIN_LOCK -> client.getSpinLock(key);
            case REDISSON_READ_LOCK -> client.getReadWriteLock(key).readLock();
            case REDISSON_WRITE_LOCK, REDISSON_READ_WRITE_LOCK -> client.getReadWriteLock(key).writeLock();
            default -> client.getLock(key);
        };
        boolean acquired = lease == 0 ? lock.tryLock(wait, TimeUnit.MILLISECONDS)
                : lock.tryLock(wait, lease, TimeUnit.MILLISECONDS);
        if (!acquired) { throw new LockException("未获取Redis锁"); }
        return guard(() -> {
            if (!lock.isHeldByCurrentThread()) { throw new LockException("执行期间锁租约已丢失"); }
            lock.unlock();
        });
    }

    private Guard semaphore(RPermitExpirableSemaphore semaphore, int permits, long wait, long lease)
            throws InterruptedException {
        Assert.isTrue(lease >= 1000 && lease <= 86400000, "信号量租约必须在1秒至1天之间");
        semaphore.trySetPermits(permits);
        Assert.isTrue(semaphore.getPermits() == permits, "同一个Redis信号量的总额度不一致");
        Duration idleTtl = Duration.ofMillis(Math.max(60000, lease * 3));
        semaphore.expire(idleTtl);
        String token = semaphore.tryAcquire(wait, lease, TimeUnit.MILLISECONDS);
        if (token == null) { throw new LockException("未获取信号量凭证"); }
        Object monitor = new Object();
        boolean[] state = new boolean[2];
        try {
            var renewal = renewals.scheduleAtFixedRate(() -> {
                synchronized (monitor) {
                    if (state[0]) { return; }
                    try {
                        if (!semaphore.updateLeaseTime(token, lease, TimeUnit.MILLISECONDS)) { state[1] = true; }
                        semaphore.expire(idleTtl);
                    } catch (RuntimeException exception) { state[1] = true; }
                }
            }, lease / 3, lease / 3, TimeUnit.MILLISECONDS);
            return guard(() -> {
                synchronized (monitor) {
                    state[0] = true;
                    renewal.cancel(false);
                    if (!semaphore.tryRelease(token) || state[1]) { throw new LockException("信号量凭证已丢失"); }
                }
            });
        } catch (RuntimeException exception) {
            try { semaphore.tryRelease(token); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    private void releaseReference(String key) {
        local.computeIfPresent(key, (ignored, slot) -> --slot.references == 0 ? null : slot);
    }

    private Guard guard(Runnable release) {
        return () -> {
            boolean interrupted = Thread.interrupted();
            try { release.run(); } finally { if (interrupted) { Thread.currentThread().interrupt(); } }
        };
    }

    @Override
    public void close() {
        closed = true;
        renewals.shutdownNow();
        local.clear();
    }

    /**
     * 获取作用域(Guard)仅表示成功持有的资源
     *
     * @author linshiqiang
     * @since 2026-10-05 16:36:32
     */
    @FunctionalInterface
    private interface Guard extends AutoCloseable {
        @Override void close();
    }

    /**
     * 本地锁槽(LocalSlot)持有者及等待者引用
     *
     * @author linshiqiang
     * @since 2026-10-05 16:36:32
     */
    private static class LocalSlot {

        /**
         * 互斥锁
         */
        private final ReentrantLock lock;

        /**
         * 信号量
         */
        private final Semaphore semaphore;

        /**
         * 总额度
         */
        private final int permits;

        /**
         * 公平策略
         */
        private final boolean fair;

        /**
         * 活动引用数，仅在Map.compute中访问
         */
        private int references;

        LocalSlot(LockRule rule) {
            permits = rule.getPermits();
            fair = rule.isFair();
            lock = new ReentrantLock(fair);
            semaphore = new Semaphore(permits, fair);
        }
    }
}
