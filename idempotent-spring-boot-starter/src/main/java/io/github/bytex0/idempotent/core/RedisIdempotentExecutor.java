package io.github.bytex0.idempotent.core;

import io.github.bytex0.idempotent.exception.IdempotentException;
import io.github.bytex0.util.MethodExpressionEvaluator;
import io.github.bytex0.util.ThrowingSupplier;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 幂等执行(RedisIdempotentExecutor)处理中锁与成功标记分离
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
public class RedisIdempotentExecutor {

    /**
     * 运行时获取的Redis客户端
     */
    private final Supplier<RedissonClient> redis;

    public RedisIdempotentExecutor(Supplier<RedissonClient> redis) { this.redis = redis; }

    public <T> T execute(String key, Duration expiry, ThrowingSupplier<T> action) throws Throwable {
        Assert.hasText(key, "幂等key不能为空");
        Assert.isTrue(expiry != null && expiry.toMillis() > 0, "幂等窗口必须大于0");
        RedissonClient client = redis.get();
        Assert.state(client != null, "幂等执行需要RedissonClient");
        String base = "common-tool:idempotent:{" + MethodExpressionEvaluator.digest(key) + "}";
        RBucket<String> completed = client.getBucket(base + ":done", StringCodec.INSTANCE);
        if (completed.isExists()) { throw new IdempotentException("请求已成功处理"); }
        RLock lock = client.getLock(base + ":processing");
        try (Guard guard = acquire(lock)) {
            if (completed.isExists()) { throw new IdempotentException("请求已成功处理"); }
            T result = action.get();
            boolean interrupted = Thread.interrupted();
            try {
                if (!lock.isHeldByCurrentThread()) { throw new IdempotentException("处理锁已丢失"); }
                completed.set("done", expiry);
            } finally {
                if (interrupted) { Thread.currentThread().interrupt(); }
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private Guard acquire(RLock lock) throws InterruptedException {
        if (lock.isHeldByCurrentThread() || !lock.tryLock(0, TimeUnit.MILLISECONDS)) {
            throw new IdempotentException("请求正在处理");
        }
        return () -> {
            boolean interrupted = Thread.interrupted();
            try {
                if (!lock.isHeldByCurrentThread()) { throw new IdempotentException("处理锁已丢失"); }
                lock.unlock();
            } finally {
                if (interrupted) { Thread.currentThread().interrupt(); }
            }
        };
    }

    /**
     * 处理锁作用域(Guard)保留业务异常并安全释放
     *
     * @author bytex0
     * @since 2026-10-05 17:09:18
     */
    @FunctionalInterface
    private interface Guard extends AutoCloseable {
        @Override void close();
    }
}
