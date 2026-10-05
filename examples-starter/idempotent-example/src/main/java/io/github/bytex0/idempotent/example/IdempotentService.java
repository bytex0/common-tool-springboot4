package io.github.bytex0.idempotent.example;

import io.github.bytex0.idempotent.aspect.Idempotent;
import io.github.bytex0.idempotent.core.IdempotentKeyGenerator;
import io.github.bytex0.idempotent.core.RedisIdempotentExecutor;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.lang.reflect.Method;

/**
 * 幂等业务(IdempotentService)共享计数模拟单次副作用
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@Service
public class IdempotentService {

    /**
     * 仅用于测试副作用和处理状态的Redis
     */
    private final RedissonClient redis;

    /**
     * 实际自动配置生成的独立执行器。
     */
    private final RedisIdempotentExecutor executor;

    /**
     * 与注解调用使用同一逻辑命名空间的键生成器。
     */
    private final IdempotentKeyGenerator keys;

    /**
     * 注解业务签名，用于独立检查与注解调用共享指纹。
     */
    private final Method runMethod;

    /**
     * 注入自动配置组件，不复制 Starter 的去重实现。
     *
     * @param redis 共享测试状态客户端
     * @param executor 幂等执行器
     * @param keys 键生成器
     * @throws NoSuchMethodException 示例签名配置错误
     */
    public IdempotentService(RedissonClient redis, RedisIdempotentExecutor executor, IdempotentKeyGenerator keys)
            throws NoSuchMethodException {
        this.redis = redis;
        this.executor = executor;
        this.keys = keys;
        this.runMethod = IdempotentService.class.getMethod("run", String.class, long.class, boolean.class);
    }

    /**
     * 在幂等作用域内执行同步副作用，失败分支发生在计数之前。
     *
     * @param key 非空业务标识
     * @param delay 延时毫秒数，范围 0 至 5000
     * @param fail 是否模拟业务失败
     * @return 累计成功次数
     * @throws InterruptedException 等待过程被中断
     */
    @Idempotent(key = "#key", expire = 1)
    public long run(String key, long delay, boolean fail) throws InterruptedException {
        Assert.isTrue(delay >= 0 && delay <= 5000, "示例延时不合法");
        var active = redis.getAtomicLong(prefix(key) + ":active");
        active.incrementAndGet();
        try {
            if (fail) {
                throw new IllegalStateException("模拟业务失败");
            }
            Thread.sleep(delay);
            var counter = redis.getAtomicLong(prefix(key) + ":count");
            long result = counter.incrementAndGet();
            counter.expire(Duration.ofSeconds(30));
            return result;
        } finally {
            active.decrementAndGet();
            active.expire(Duration.ofSeconds(30));
        }
    }

    /**
     * 使用原独立检查入口预占一秒窗口；预占后不再调用受同键保护的方法。
     *
     * @param key 非空业务标识，与 run 共享去重域
     */
    public void reserve(String key) {
        Assert.hasText(key, "业务标识不能为空");
        String logical = keys.generateKey("#key", "idempotent:", this, runMethod, new Object[]{key, 0L, false});
        executor.execute(logical, 1);
    }

    /**
     * 查询测试业务当前执行数。
     *
     * @param key 业务标识
     * @return 当前执行数
     */
    public long active(String key) {
        return redis.getAtomicLong(prefix(key) + ":active").get();
    }

    /**
     * 查询累计成功副作用次数。
     *
     * @param key 业务标识
     * @return 累计成功数
     */
    public long count(String key) {
        return redis.getAtomicLong(prefix(key) + ":count").get();
    }

    /**
     * 隔离测试状态，不将原业务标识明文放入 Redis 键。
     *
     * @param key 业务标识
     * @return 测试键前缀
     */
    private String prefix(String key) {
        Assert.hasText(key, "业务标识不能为空");
        return "test:idempotent:" + MethodExpressionEvaluator.digest(key);
    }
}
