package io.github.bytex0.ratelimiter.example;

import io.github.bytex0.ratelimiter.aspect.RateLimiter;
import io.github.bytex0.ratelimiter.core.RateLimiterFactory;
import io.github.bytex0.ratelimiter.core.impl.AbstractRedisRateLimiterStrategy;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.enums.RedisClientType;
import io.github.bytex0.ratelimiter.exception.RateLimitException;
import io.github.bytex0.ratelimiter.model.FlowRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 限流业务(RateService)程序化和注解调用
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@Service
public class RateService {

    /**
     * 支持用户策略覆盖的真实限流工厂。
     */
    private final RateLimiterFactory factory;

    /**
     * 原 Lua 策略类，用于验证公开可变参数接口兼容性。
     */
    private final List<AbstractRedisRateLimiterStrategy> redisStrategies;

    /**
     * 注入当前上下文的策略工厂。
     *
     * @param factory 策略工厂
     * @param redisStrategies 原 Lua 策略实现
     */
    public RateService(RateLimiterFactory factory, List<AbstractRedisRateLimiterStrategy> redisStrategies) {
        this.factory = factory;
        this.redisStrategies = List.copyOf(redisStrategies);
    }

    /**
     * 程序化申请许可，拒绝时抛出业务异常。
     *
     * @param rule 完整业务规则
     * @throws RateLimitException 额度不足
     */
    public void acquire(FlowRule rule) {
        if (!factory.tryAccess(rule)) {
            throw new RateLimitException();
        }
    }

    /**
     * 按旧参数顺序调用具体 Lua 策略，测试键使用独立命名空间。
     *
     * @param rule 测试规则，只支持四种 Lua 算法
     * @throws RateLimitException 当前额度不足
     */
    public void legacy(FlowRule rule) {
        AbstractRedisRateLimiterStrategy strategy = redisStrategies.stream()
                .filter(candidate -> candidate.getType() == rule.getRateLimiterType()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("原参数接口只支持Lua策略"));
        FlowRule isolated = rule.toBuilder().key("common-tool:rate:legacy:" + rule.getRateLimiterType()
                + ":" + MethodExpressionEvaluator.digest(rule.getKey())).build();
        Object[] arguments = switch (rule.getRateLimiterType()) {
            case REDIS_LUA_FIXED_WINDOW -> new Object[]{rule.getWindowTime(), rule.getMaxRequests()};
            case REDIS_LUA_SLIDING_WINDOW -> new Object[]{rule.getWindowTime(), rule.getMaxRequests(),
                    System.currentTimeMillis(), UUID.randomUUID().toString()};
            case REDIS_LUA_TOKEN_BUCKET -> new Object[]{rule.getBucketCapacity(), rule.getTokenRate(),
                    System.currentTimeMillis()};
            case REDIS_LUA_LEAKY_BUCKET -> new Object[]{rule.getBucketCapacity(), rule.getTokenRate(),
                    System.currentTimeMillis(), rule.getPermits()};
            default -> throw new IllegalArgumentException("原参数接口只支持Lua策略");
        };
        if (!strategy.tryAccess(isolated, arguments)) {
            throw new RateLimitException();
        }
    }

    /**
     * 演示通过代理调用的 Redisson 限流。
     *
     * @param key 每两秒最多申请两次的共享业务键
     */
    @RateLimiter(type = RateLimiterType.REDIS_LUA_FIXED_WINDOW, key = "#key", maxRequests = 2, windowTime = 2)
    public void annotated(String key) {
    }

    /**
     * 演示相同业务键通过另一个 Redis 客户端仍共享同一窗口。
     *
     * @param key 每两秒最多申请两次的共享业务键
     */
    @RateLimiter(type = RateLimiterType.REDIS_LUA_FIXED_WINDOW, redisClientType = RedisClientType.REDIS_TEMPLATE,
            key = "#key", maxRequests = 2, windowTime = 2)
    public void annotatedSpring(String key) {
    }
}
