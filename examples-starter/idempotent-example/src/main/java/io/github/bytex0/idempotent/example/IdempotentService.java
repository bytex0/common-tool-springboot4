package io.github.bytex0.idempotent.example;

import io.github.bytex0.idempotent.aspect.Idempotent;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;

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

    public IdempotentService(RedissonClient redis) { this.redis = redis; }

    @Idempotent(key = "#key", expire = 1)
    public long run(String key, long delay, boolean fail) throws InterruptedException {
        Assert.isTrue(delay >= 0 && delay <= 5000, "示例延时不合法");
        var active = redis.getAtomicLong(prefix(key) + ":active");
        active.incrementAndGet();
        try {
            if (fail) { throw new IllegalStateException("模拟业务失败"); }
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

    public long active(String key) { return redis.getAtomicLong(prefix(key) + ":active").get(); }

    public long count(String key) { return redis.getAtomicLong(prefix(key) + ":count").get(); }

    private String prefix(String key) { return "test:idempotent:" + MethodExpressionEvaluator.digest(key); }
}
