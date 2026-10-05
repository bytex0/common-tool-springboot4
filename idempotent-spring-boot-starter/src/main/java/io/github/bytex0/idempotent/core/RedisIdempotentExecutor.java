package io.github.bytex0.idempotent.core;

import io.github.bytex0.idempotent.exception.IdempotentException;
import io.github.bytex0.idempotent.config.IdempotentProperties;
import io.github.bytex0.util.MethodExpressionEvaluator;
import io.github.bytex0.util.ThrowingSupplier;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
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
     * 仅记录阶段与窗口，不记录业务键或方法参数。
     */
    private static final Logger LOG = LoggerFactory.getLogger(RedisIdempotentExecutor.class);

    /**
     * 运行时获取的Redis客户端
     */
    private final Supplier<RedissonClient> redis;

    /**
     * 原默认窗口与调试配置。
     */
    private final IdempotentProperties properties;

    /**
     * 保留原直接客户端构造方式，客户端由调用方管理。
     *
     * @param client Redis 客户端
     * @param properties 幂等配置
     */
    public RedisIdempotentExecutor(RedissonClient client, IdempotentProperties properties) {
        this(() -> client, properties);
    }

    /**
     * 保留按需客户端构造方式，使用默认配置。
     *
     * @param redis 客户端提供器
     */
    public RedisIdempotentExecutor(Supplier<RedissonClient> redis) {
        this(redis, new IdempotentProperties());
    }

    /**
     * 注入按需客户端与配置，构造时不连接 Redis。
     *
     * @param redis 客户端提供器
     * @param properties 当前配置
     */
    public RedisIdempotentExecutor(Supplier<RedissonClient> redis, IdempotentProperties properties) {
        this.redis = Objects.requireNonNull(redis);
        this.properties = Objects.requireNonNull(properties);
    }

    /**
     * 暴露不可变的默认窗口，供原切面构造方式继续采用执行器配置。
     *
     * @return 默认去重时长
     */
    public Duration getDefaultExpireSeconds() {
        return properties.getDefaultExpireSeconds();
    }

    /**
     * 保留原独立去重检查：成功返回即占用窗口，不能感知随后业务是否成功。
     * 原键仍写入时间戳，并同步记录新作用域标记，避免本版本两种入口重复放行。
     *
     * @param key 原业务键，调用方负责其命名空间
     * @param expireSeconds 窗口秒数，不大于零时使用默认窗口
     * @throws IdempotentException 请求重复或正在处理中
     */
    public void execute(String key, long expireSeconds) {
        Duration expiry = expireSeconds > 0 ? Duration.ofSeconds(expireSeconds) : properties.getDefaultExpireSeconds();
        validate(key, expiry);
        RedissonClient client = client();
        String base = base(key);
        RBucket<String> completed = client.getBucket(base + ":done", StringCodec.INSTANCE);
        try (Guard guard = acquire(client.getLock(base + ":processing"))) {
            if (completed.isExists()) {
                throw new IdempotentException("重复请求");
            }
            RBucket<Object> original = client.getBucket(key);
            if (!original.setIfAbsent(System.currentTimeMillis(), expiry)) {
                throw new IdempotentException("重复请求");
            }
            completed.set("legacy", expiry);
            debug("legacy-reserved", expiry);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("幂等检查被中断", exception);
        }
    }

    /**
     * 用处理锁包围同步业务，只有业务成功才记录成功窗口。
     *
     * @param key 业务键
     * @param expiry 成功窗口，至少一毫秒
     * @param action 非空同步业务回调
     * @param <T> 业务结果类型
     * @return 业务结果
     * @throws Throwable 原始业务异常、存储异常或中断
     */
    public <T> T execute(String key, Duration expiry, ThrowingSupplier<T> action) throws Throwable {
        validate(key, expiry);
        Assert.notNull(action, "业务回调不能为空");
        RedissonClient client = client();
        String base = base(key);
        RBucket<String> completed = client.getBucket(base + ":done", StringCodec.INSTANCE);
        if (completed.isExists()) {
            throw new IdempotentException("请求已成功处理");
        }
        RLock lock = client.getLock(base + ":processing");
        try (Guard guard = acquire(lock)) {
            if (completed.isExists()) {
                throw new IdempotentException("请求已成功处理");
            }
            debug("processing", expiry);
            T result = action.get();
            boolean interrupted = Thread.interrupted();
            try {
                if (!lock.isHeldByCurrentThread()) {
                    throw new IdempotentException("处理锁已丢失");
                }
                completed.set("done", expiry);
                debug("completed", expiry);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    /**
     * 校验键及窗口，保证错误配置在获取外部资源之前失败。
     *
     * @param key 业务键
     * @param expiry 去重窗口
     */
    private void validate(String key, Duration expiry) {
        Assert.hasText(key, "幂等key不能为空");
        Assert.isTrue(expiry != null && expiry.toMillis() > 0, "幂等窗口必须大于0");
    }

    /**
     * 在执行时获取客户端，避免仅添加依赖就访问外部服务。
     *
     * @return 当前客户端
     */
    private RedissonClient client() {
        RedissonClient client = redis.get();
        Assert.state(client != null, "幂等执行需要RedissonClient");
        return client;
    }

    /**
     * 生成处理锁和标记的同槽前缀，不把业务键明文写入内部键。
     *
     * @param key 业务键
     * @return Redis hash tag 前缀
     */
    private String base(String key) {
        return "common-tool:idempotent:{" + MethodExpressionEvaluator.digest(key) + "}";
    }

    /**
     * 记录可选 DEBUG 事件，日志级别仍由应用控制。
     *
     * @param stage 固定操作阶段
     * @param expiry 去重时长
     */
    private void debug(String stage, Duration expiry) {
        if (Boolean.TRUE.equals(properties.getDebugLog())) {
            LOG.debug("Idempotent stage={}, window={}", stage, expiry);
        }
    }

    /**
     * 获取不可重入的业务处理作用域，仅成功获取者可以释放。
     *
     * @param lock Redisson 处理锁
     * @return 关闭时检查所有权的释放动作
     * @throws InterruptedException 获取过程被中断
     */
    private Guard acquire(RLock lock) throws InterruptedException {
        if (lock.isHeldByCurrentThread() || !lock.tryLock(0, TimeUnit.MILLISECONDS)) {
            throw new IdempotentException("请求正在处理");
        }
        return () -> {
            boolean interrupted = Thread.interrupted();
            try {
                if (!lock.isHeldByCurrentThread()) {
                    throw new IdempotentException("处理锁已丢失");
                }
                lock.unlock();
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
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

        /**
         * 释放已经成功取得的处理锁，保留异常及中断语义。
         */
        @Override
        void close();
    }
}
