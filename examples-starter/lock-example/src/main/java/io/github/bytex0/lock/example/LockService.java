package io.github.bytex0.lock.example;

import io.github.bytex0.lock.aspect.Lock;
import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.core.LockFactory;
import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.enums.RedisClientType;
import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.model.LockRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;

/**
 * 锁业务(LockService)通过共享计数验证跨实例并发限制
 *
 * @author bytex0
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

    /**
     * 自动配置注册的策略工厂。
     */
    private final LockFactory factory;

    /**
     * 注入真实 Starter 组件。
     *
     * @param template 锁模板
     * @param redis 测试计数客户端
     * @param factory 策略工厂
     */
    public LockService(LockTemplate template, RedissonClient redis, LockFactory factory) {
        this.template = template;
        this.redis = redis;
        this.factory = factory;
    }

    /**
     * 程序化调用模板，两个分布式信号量都使用 1.5 秒可续租期限。
     *
     * @param key 业务键
     * @param type 锁类型
     * @param permits 总额度
     * @param wait 等待毫秒数，范围 0 至 5000
     * @param delay 业务延时毫秒数，范围 0 至 5000
     * @param fail 是否模拟失败
     * @return 进入业务时的共享并发数
     * @throws Throwable 业务或锁错误
     */
    public long run(String key, LockType type, int permits, long wait, long delay, boolean fail) throws Throwable {
        Assert.isTrue(delay >= 0 && delay <= 5000 && wait >= 0 && wait <= 5000, "示例时间参数不合法");
        LockRule rule = LockRule.builder().key(key).lockType(type).permits(permits).timeout(wait)
                .leaseTime(type == LockType.REDISSON_SEMAPHORE || type == LockType.REDIS_TEMPLATE_SEMAPHORE ? 1500 : 0).build();
        return template.execute(rule, () -> work(key, delay, fail));
    }

    /**
     * 通过服务代理验证静态注解和参数表达式。
     *
     * @param key 业务键
     * @return 共享并发数
     * @throws InterruptedException 业务延时被中断
     */
    @Lock(type = LockType.REDISSON_LOCK, key = "#key")
    public long annotated(String key) throws InterruptedException {
        return work(key, 20, false);
    }

    /**
     * 使用原工厂限时获取和显式释放，不绕过所有权检查。
     *
     * @param key 业务键
     * @param type 锁类型
     * @return 并发数
     * @throws InterruptedException 业务延时被中断
     */
    public long factory(String key, LockType type) throws InterruptedException {
        LockRule rule = LockRule.builder().key(key).lockType(type).timeout(3000L).build();
        if (!factory.tryLock(rule)) {
            throw new LockException("未获取示例工厂锁");
        }
        try {
            return work(key, 50, false);
        } finally {
            factory.unlock(rule);
        }
    }

    /**
     * 非空完整规则优先于 enable=false，并选择真实 RedisTemplate 后端。
     *
     * @param key 业务键
     * @param permits 动态总额度
     * @return 并发数
     * @throws InterruptedException 业务延时被中断
     */
    @Lock(enable = false, ruleFunction = "@lockService.rule(#key, #permits)")
    public long dynamic(String key, int permits) throws InterruptedException {
        return work(key, 80, false);
    }

    /**
     * 验证原 permitsFunction 动态额度入口。
     *
     * @param key 业务键
     * @param permits 本地总额度
     * @return 并发数
     * @throws InterruptedException 业务延时被中断
     */
    @Lock(type = LockType.SEMAPHORE, key = "#key", permitsFunction = "#permits")
    public long permits(String key, int permits) throws InterruptedException {
        return work(key, 80, false);
    }

    /**
     * 供可信 SpEL Bean 引用返回完整规则。
     *
     * @param key 业务键
     * @param permits 总额度
     * @return 使用 RedisTemplate 的分布式信号量规则
     */
    public LockRule rule(String key, int permits) {
        return LockRule.builder().key(key).lockType(LockType.REDISSON_SEMAPHORE)
                .redisClientType(RedisClientType.REDIS_TEMPLATE).permits(permits).timeout(3000L).leaseTime(1500).build();
    }

    /**
     * 查询跨实例共享的活动业务数。
     *
     * @param key 业务键
     * @return 当前活动数
     */
    public long active(String key) {
        return redis.getAtomicLong("test:lock:active:" + MethodExpressionEvaluator.digest(key)).get();
    }

    /**
     * 在实际锁保护下更新共享状态，退出时清理活动数并设置测试 TTL。
     *
     * @param key 业务键
     * @param delay 延时毫秒数
     * @param fail 是否模拟失败
     * @return 进入时的活动数
     * @throws InterruptedException 业务等待被中断
     */
    private long work(String key, long delay, boolean fail) throws InterruptedException {
        var counter = redis.getAtomicLong("test:lock:active:" + MethodExpressionEvaluator.digest(key));
        long active = counter.incrementAndGet();
        try {
            if (fail) {
                throw new IllegalStateException("模拟业务失败");
            }
            Thread.sleep(delay);
            return active;
        } finally {
            counter.decrementAndGet();
            counter.expire(Duration.ofSeconds(30));
        }
    }
}
