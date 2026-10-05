package io.github.bytex0.lock.core;

import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.enums.RedisClientType;
import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.model.LockRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import io.github.bytex0.util.ThrowingSupplier;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 锁执行(LockTemplate)以作用域保证成功获取后才释放
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
public class LockTemplate implements AutoCloseable {

    /**
     * 在途作用域上限，防止本地键与续租任务无限积压。
     */
    public static final int DEFAULT_MAX_SCOPES = 4096;

    /**
     * 信号量默认租约毫秒数。
     */
    private static final long DEFAULT_LEASE_MILLIS = 30_000;

    /**
     * 信号量最大租约毫秒数。
     */
    private static final long MAX_LEASE_MILLIS = 86_400_000;

    /**
     * Redis 后端仅在实际使用时解析。
     */
    private final Supplier<RedissonClient> redis;

    /**
     * 不受模板默认序列化器影响的 Lua 信号量后端。
     */
    private final TemplateSemaphore semaphoreBackend;

    /**
     * 本地活动键及等待者，最后一个引用离开时移除。
     */
    private final Map<String, LocalSlot> local = new ConcurrentHashMap<>();

    /**
     * 队列任务数受作用域上限约束，取消后立即移除。
     */
    private final ScheduledThreadPoolExecutor renewals;

    /**
     * 只保护关闭标志与计数，不持锁访问网络或执行业务。
     */
    private final ReentrantLock lifecycle = new ReentrantLock();

    /**
     * 持有者与等待者的总容量。
     */
    private final int maxScopes;

    /**
     * 生命周期锁内访问的在途计数。
     */
    private int scopes;

    /**
     * 关闭后拒绝新请求，原持有者继续续租直到释放。
     */
    private boolean closed;

    /**
     * 保留原按需 Redisson 构造方式。
     *
     * @param redis 外部管理的客户端提供器
     */
    public LockTemplate(Supplier<RedissonClient> redis) {
        this(redis, () -> null, DEFAULT_MAX_SCOPES);
    }

    /**
     * 创建双后端模板，外部客户端由调用方关闭。
     *
     * @param redis Redisson 提供器
     * @param templates RedisTemplate 提供器
     * @param maxScopes 在途作用域上限，必须为正数
     */
    public LockTemplate(Supplier<RedissonClient> redis, Supplier<RedisTemplate<?, ?>> templates, int maxScopes) {
        this.redis = Objects.requireNonNull(redis);
        this.semaphoreBackend = new TemplateSemaphore(Objects.requireNonNull(templates));
        Assert.isTrue(maxScopes > 0, "锁作用域上限必须大于零");
        this.maxScopes = maxScopes;
        renewals = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform().daemon(true)
                .name("common-tool-lock-renewal-", 0).factory());
        renewals.setRemoveOnCancelPolicy(true);
    }

    /**
     * 按规则等待并执行同步业务，成功获取后才释放。
     *
     * @param rule 进入时复制的规则，调用方不得并发修改
     * @param action 同步回调，作用域须包含业务提交
     * @param <T> 结果类型
     * @return 回调结果
     * @throws Throwable 原业务异常、锁错误或中断
     */
    public <T> T execute(LockRule rule, ThrowingSupplier<T> action) throws Throwable {
        Assert.notNull(action, "业务回调不能为空");
        try (LockHandle handle = acquire(rule, false)) {
            return action.get();
        }
    }

    /**
     * 获取手动作用域，必须在同线程关闭。
     *
     * @param rule 锁规则
     * @param indefinitely true 为原 lock 的可中断无限等待，false 按规则等待
     * @return 已成功获取的句柄
     * @throws InterruptedException 等待被中断，保持中断标志
     */
    public LockHandle acquire(LockRule rule, boolean indefinitely) throws InterruptedException {
        Assert.notNull(rule, "锁规则不能为空");
        LockRule snapshot = rule.toBuilder().build();
        enter();
        boolean success = false;
        try {
            if (!snapshot.isEnable()) {
                LockHandle handle = owned(() -> { });
                success = true;
                return handle;
            }
            validate(snapshot);
            long wait = indefinitely ? Long.MAX_VALUE
                    : snapshot.isBlock() ? millis(snapshot.getTimeout(), snapshot.getTimeUnit()) : 0;
            LockHandle handle = owned(acquireResource(snapshot, wait));
            success = true;
            return handle;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } finally {
            if (!success) {
                leave();
            }
        }
    }

    /**
     * 查询仍被持有或等待的本地键数。
     *
     * @return 活动键数
     */
    public int activeLocalKeys() {
        return local.size();
    }

    /**
     * 校验输入，错误规则不触发网络连接。
     *
     * @param rule 规则快照
     */
    private void validate(LockRule rule) {
        Assert.hasText(rule.getKey(), "锁key不能为空");
        Assert.notNull(rule.getLockType(), "锁类型不能为空");
        Assert.notNull(rule.getTimeUnit(), "时间单位不能为空");
        Assert.isTrue(rule.getPermits() != null && rule.getPermits() > 0, "锁额度必须大于零");
        Assert.isTrue(rule.getTimeout() != null && rule.getTimeout() >= 0 && rule.getLeaseTime() >= 0, "锁时间不合法");
    }

    /**
     * 正时长最少一毫秒，避免短租约被截断为 watchdog。
     *
     * @param value 原时长
     * @param unit 时间单位
     * @return 毫秒数
     */
    private long millis(long value, TimeUnit unit) {
        return value == 0 ? 0 : Math.max(1, unit.toMillis(value));
    }

    /**
     * 成功取得后端资源时才返回释放动作。
     *
     * @param rule 规则快照
     * @param wait 最大等待毫秒数
     * @return 释放动作
     * @throws InterruptedException 获取被中断
     */
    private Runnable acquireResource(LockRule rule, long wait) throws InterruptedException {
        LockType type = rule.getLockType();
        String group = switch (type) {
            case REDISSON_READ_LOCK, REDISSON_WRITE_LOCK, REDISSON_READ_WRITE_LOCK -> "RW";
            default -> type.name();
        };
        String key = "common-tool:lock:" + group + ":{" + MethodExpressionEvaluator.digest(rule.getKey()) + "}";
        if (type == LockType.REENTRANT_LOCK || type == LockType.SEMAPHORE) {
            return acquireLocal(rule, key, wait);
        }
        long lease = millis(rule.getLeaseTime(), rule.getTimeUnit());
        if (type == LockType.REDISSON_SEMAPHORE || type == LockType.REDIS_TEMPLATE_SEMAPHORE) {
            long duration = lease == 0 ? DEFAULT_LEASE_MILLIS : lease;
            Assert.isTrue(duration >= 1000 && duration <= MAX_LEASE_MILLIS, "信号量租约必须在1秒至1天之间");
            if (type == LockType.REDIS_TEMPLATE_SEMAPHORE || rule.getRedisClientType() == RedisClientType.REDIS_TEMPLATE) {
                TemplateSemaphore.Permit permit = semaphoreBackend.acquire(rule.getKey(), rule.getPermits(), wait, duration);
                return renew(permit::renew, permit::release, duration);
            }
            return acquireSemaphore(client().getPermitExpirableSemaphore(key), rule.getPermits(), wait, duration);
        }
        Assert.isTrue(rule.getRedisClientType() != RedisClientType.REDIS_TEMPLATE, "当前互斥锁类型仅支持Redisson");
        RedissonClient client = client();
        RLock lock = switch (type) {
            case REDISSON_FAIR_LOCK -> client.getFairLock(key);
            case REDISSON_SPIN_LOCK -> client.getSpinLock(key);
            case REDISSON_READ_LOCK -> client.getReadWriteLock(key).readLock();
            case REDISSON_WRITE_LOCK, REDISSON_READ_WRITE_LOCK -> client.getReadWriteLock(key).writeLock();
            default -> client.getLock(key);
        };
        boolean acquired = lease == 0 ? lock.tryLock(wait, TimeUnit.MILLISECONDS)
                : lock.tryLock(wait, lease, TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new LockException("未获取Redis锁");
        }
        return () -> {
            if (!lock.isHeldByCurrentThread()) {
                throw new LockException("执行期间锁租约已丢失");
            }
            lock.unlock();
        };
    }

    /**
     * 按需取得实际 Redisson 客户端。
     *
     * @return 客户端
     */
    private RedissonClient client() {
        RedissonClient client = redis.get();
        Assert.state(client != null, "Redis锁策略需要RedissonClient");
        return client;
    }

    /**
     * 本地持有者与等待者引用同一槽，失败不增加信号量额度。
     *
     * @param rule 规则
     * @param key 隔离后的键
     * @param wait 等待毫秒数
     * @return 释放动作
     * @throws InterruptedException 获取被中断
     */
    private Runnable acquireLocal(LockRule rule, String key, long wait) throws InterruptedException {
        LocalSlot slot = local.compute(key, (ignored, previous) -> {
            LocalSlot next = previous == null ? new LocalSlot(rule) : previous;
            boolean samePermits = rule.getLockType() != LockType.SEMAPHORE || next.permits == rule.getPermits();
            Assert.isTrue(samePermits && next.fair == rule.isFair(), "同一个锁键的配置不一致");
            next.references++;
            return next;
        });
        boolean acquired = false;
        try {
            acquired = rule.getLockType() == LockType.SEMAPHORE
                    ? slot.semaphore.tryAcquire(wait, TimeUnit.MILLISECONDS) : slot.lock.tryLock(wait, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new LockException("未获取锁");
            }
            return () -> {
                try {
                    if (rule.getLockType() == LockType.SEMAPHORE) {
                        slot.semaphore.release();
                    } else {
                        slot.lock.unlock();
                    }
                } finally {
                    releaseReference(key);
                }
            };
        } finally {
            if (!acquired) {
                releaseReference(key);
            }
        }
    }

    /**
     * Redisson 带期限凭证的续租和释放均携带唯一 token。
     *
     * @param semaphore 分布式信号量
     * @param permits 总额度
     * @param wait 最大等待毫秒数
     * @param lease 租约毫秒数
     * @return 释放动作
     * @throws InterruptedException 等待被中断
     */
    private Runnable acquireSemaphore(RPermitExpirableSemaphore semaphore, int permits, long wait, long lease)
            throws InterruptedException {
        semaphore.trySetPermits(permits);
        Assert.isTrue(semaphore.getPermits() == permits, "同一个Redis信号量的总额度不一致");
        Duration idleTtl = Duration.ofMillis(MAX_LEASE_MILLIS * 3);
        semaphore.expire(idleTtl);
        String token = semaphore.tryAcquire(wait, lease, TimeUnit.MILLISECONDS);
        if (token == null) {
            throw new LockException("未获取信号量凭证");
        }
        return renew(() -> {
            boolean renewed = semaphore.updateLeaseTime(token, lease, TimeUnit.MILLISECONDS);
            if (renewed) {
                semaphore.expire(idleTtl);
            }
            return renewed;
        }, () -> semaphore.tryRelease(token), lease);
    }

    /**
     * 凭证保证续租与释放可并发，原子状态拒绝关闭后的迟到结果。
     * 后端续租不得重新创建已经释放或过期的凭证，网络命令超时由客户端配置约束。
     *
     * @param renewal 延长当前凭证，返回是否仍有归属
     * @param release 释放当前凭证
     * @param lease 租约毫秒数
     * @return 取消续租并释放的动作
     */
    private Runnable renew(BooleanSupplier renewal, BooleanSupplier release, long lease) {
        AtomicReference<LeaseState> state = new AtomicReference<>(LeaseState.ACTIVE);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        try {
            var task = renewals.scheduleAtFixedRate(() -> {
                if (state.get() != LeaseState.ACTIVE) {
                    return;
                }
                try {
                    if (!renewal.getAsBoolean()) {
                        state.compareAndSet(LeaseState.ACTIVE, LeaseState.LOST);
                    }
                } catch (RuntimeException exception) {
                    failure.compareAndSet(null, exception);
                    state.compareAndSet(LeaseState.ACTIVE, LeaseState.LOST);
                }
            }, lease / 3, lease / 3, TimeUnit.MILLISECONDS);
            return () -> {
                LeaseState previous = state.getAndSet(LeaseState.CLOSED);
                task.cancel(false);
                if (!release.getAsBoolean() || previous == LeaseState.LOST) {
                    throw new LockException("信号量凭证已丢失", failure.get());
                }
            };
        } catch (RuntimeException exception) {
            try {
                release.getAsBoolean();
            } catch (RuntimeException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    /**
     * 释放动作绑定当前线程，重复关闭不多释放凭证。
     *
     * @param release 后端释放动作
     * @return 所有权句柄
     */
    private LockHandle owned(Runnable release) {
        Thread owner = Thread.currentThread();
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            Assert.state(Thread.currentThread() == owner, "只能由获取线程释放锁");
            if (!released.compareAndSet(false, true)) {
                return;
            }
            boolean interrupted = Thread.interrupted();
            try {
                release.run();
            } finally {
                leave();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    /**
     * 原子减少本地槽引用。
     *
     * @param key 本地键
     */
    private void releaseReference(String key) {
        local.computeIfPresent(key, (ignored, slot) -> --slot.references == 0 ? null : slot);
    }

    /**
     * 准入与关闭在短临界区排序，不持锁调用业务或网络。
     */
    private void enter() {
        lifecycle.lock();
        try {
            Assert.state(!closed, "锁模板已关闭");
            Assert.state(scopes < maxScopes, "锁作用域容量已满");
            scopes++;
        } finally {
            lifecycle.unlock();
        }
    }

    /**
     * 关闭后最后一个作用域负责停止续租线程。
     */
    private void leave() {
        lifecycle.lock();
        try {
            scopes--;
            if (closed && scopes == 0) {
                renewals.shutdown();
            }
        } finally {
            lifecycle.unlock();
        }
    }

    /**
     * 拒绝新调用但不清空在途锁、不打断续租，持有者仍须释放。
     */
    @Override
    public void close() {
        lifecycle.lock();
        try {
            closed = true;
            if (scopes == 0) {
                renewals.shutdown();
            }
        } finally {
            lifecycle.unlock();
        }
    }

    /**
     * 租约状态(LeaseState)协调续租与关闭。
     *
     * @author linshiqiang
     * @since 2026-10-06 01:53:24
     */
    private enum LeaseState {

        /**
         * 正常续租。
         */
        ACTIVE,

        /**
         * 续租失败，释放时报告丢失。
         */
        LOST,

        /**
         * 已关闭，不再接受迟到结果。
         */
        CLOSED
    }

    /**
     * 本地锁槽(LocalSlot)持有者及等待者引用
     *
     * @author bytex0
     * @since 2026-10-05 16:36:32
     */
    private static class LocalSlot {

        /**
         * 本地互斥锁。
         */
        private final ReentrantLock lock;

        /**
         * 本地信号量。
         */
        private final Semaphore semaphore;

        /**
         * 固定总额度。
         */
        private final int permits;

        /**
         * 固定公平策略。
         */
        private final boolean fair;

        /**
         * 仅在 Map.compute 内修改的引用数。
         */
        private int references;

        /**
         * 构造一个活动键的本地状态。
         *
         * @param rule 当前规则
         */
        LocalSlot(LockRule rule) {
            permits = rule.getPermits();
            fair = rule.isFair();
            lock = new ReentrantLock(fair);
            semaphore = new Semaphore(permits, fair);
        }
    }
}
