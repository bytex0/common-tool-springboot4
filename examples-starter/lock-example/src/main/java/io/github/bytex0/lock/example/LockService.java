package io.github.bytex0.lock.example;

import io.github.bytex0.lock.aspect.Lock;
import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.model.LockRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;

/**
 * 锁业务(LockService)通过共享计数验证跨实例并发限制
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
@Service
public class LockService {

    /**
     * 真实锁模板
     */
    private final LockTemplate template;

    /**
     * 共享测试计数器客户端
     */
    private final RedissonClient redis;

    public LockService(LockTemplate template, RedissonClient redis) { this.template = template; this.redis = redis; }

    public long run(String key, LockType type, int permits, long wait, long delay, boolean fail) throws Throwable {
        Assert.isTrue(delay >= 0 && delay <= 5000 && wait >= 0 && wait <= 5000, "示例时间参数不合法");
        LockRule rule = LockRule.builder().key(key).lockType(type).permits(permits).timeout(wait)
                .leaseTime(type == LockType.REDISSON_SEMAPHORE ? 1500 : 0).build();
        return template.execute(rule, () -> work(key, delay, fail));
    }

    @Lock(type = LockType.REDISSON_LOCK, key = "#key")
    public long annotated(String key) throws InterruptedException { return work(key, 20, false); }

    public long active(String key) {
        return redis.getAtomicLong("test:lock:active:" + MethodExpressionEvaluator.digest(key)).get();
    }

    private long work(String key, long delay, boolean fail) throws InterruptedException {
        var counter = redis.getAtomicLong("test:lock:active:" + MethodExpressionEvaluator.digest(key));
        long active = counter.incrementAndGet();
        try {
            if (fail) { throw new IllegalStateException("模拟业务失败"); }
            Thread.sleep(delay);
            return active;
        } finally {
            counter.decrementAndGet();
            counter.expire(Duration.ofSeconds(30));
        }
    }
}
