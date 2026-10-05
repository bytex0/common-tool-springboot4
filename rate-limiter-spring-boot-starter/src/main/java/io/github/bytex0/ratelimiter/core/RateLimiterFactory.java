package io.github.bytex0.ratelimiter.core;

import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.model.FlowRule;
import io.github.bytex0.ratelimiter.exception.RateLimitException;
import io.github.bytex0.ratelimiter.core.impl.GuavaRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.LocalRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisFixedWindowRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisLeakyBucketRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisSlidingWindowRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedisTokenBucketRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.core.impl.RedissonRateLimiterStrategyImpl;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.util.Assert;

/**
 * 实例隔离的限流策略工厂，自定义策略优先，其余类型交给内置模板。
 *
 * @author bytex0
 * @since 2026-10-05 20:36:23
 */
public class RateLimiterFactory implements CommandLineRunner {

    /**
     * 仅这些精确类型属于内置策略；用户子类及自定义实现均可覆盖对应算法。
     */
    private static final Set<Class<?>> BUILT_IN_TYPES = Set.of(LocalRateLimiterStrategyImpl.class,
            GuavaRateLimiterStrategyImpl.class, RedissonRateLimiterStrategyImpl.class,
            RedisFixedWindowRateLimiterStrategyImpl.class, RedisSlidingWindowRateLimiterStrategyImpl.class,
            RedisTokenBucketRateLimiterStrategyImpl.class, RedisLeakyBucketRateLimiterStrategyImpl.class);

    /**
     * 当前上下文的后备算法实现，使用原单参数构造器时为空。
     */
    private final RateLimiterTemplate template;

    /**
     * 构造时完成校验并固定的自定义策略，不使用静态注册表。
     */
    private final Map<RateLimiterType, RateLimiterStrategy> strategies;

    /**
     * 保留原策略列表构造器，没有匹配策略时按原契约抛出业务异常。
     *
     * @param strategyList 策略列表
     */
    public RateLimiterFactory(List<RateLimiterStrategy> strategyList) {
        this(null, strategyList);
    }

    /**
     * 创建策略工厂，重复的自定义算法类型立即失败。
     *
     * @param template 后备实现，为 null 时仅使用显式策略
     * @param strategyList 用户策略 Bean 列表
     */
    public RateLimiterFactory(RateLimiterTemplate template, List<RateLimiterStrategy> strategyList) {
        this.template = template;
        Map<RateLimiterType, RateLimiterStrategy> registry = new EnumMap<>(RateLimiterType.class);
        Map<RateLimiterType, RateLimiterStrategy> custom = new EnumMap<>(RateLimiterType.class);
        for (RateLimiterStrategy strategy : strategyList) {
            Assert.notNull(strategy.getType(), "自定义限流类型不能为空");
            Map<RateLimiterType, RateLimiterStrategy> selected =
                    BUILT_IN_TYPES.contains(AopUtils.getTargetClass(strategy)) ? registry : custom;
            Assert.isTrue(selected.putIfAbsent(strategy.getType(), strategy) == null, "重复的限流策略");
        }
        registry.putAll(custom);
        strategies = Map.copyOf(registry);
    }

    /**
     * 保留原工厂的空规则和关闭规则直接放行行为。
     *
     * @param flowRule 业务规则，可为空；执行期间不得修改
     * @return 是否允许访问
     */
    public boolean tryAccess(FlowRule flowRule) {
        if (flowRule == null || !Boolean.TRUE.equals(flowRule.getEnable())) {
            return true;
        }
        Assert.notNull(flowRule.getRateLimiterType(), "限流类型不能为空");
        RateLimiterStrategy strategy = strategies.get(flowRule.getRateLimiterType());
        if (strategy != null) {
            return strategy.tryAccess(flowRule);
        }
        if (template == null) {
            throw new RateLimitException("未找到对应的限流策略类型: " + flowRule.getRateLimiterType());
        }
        return template.tryAccess(flowRule);
    }

    /**
     * 保留原启动回调入口，注册已在构造期完成，避免启动前无法使用策略。
     *
     * @param args 应用启动参数，不影响策略选择
     */
    @Override
    public void run(String... args) {
        // 不延迟到启动回调注册，防止早期业务调用读取未初始化的策略表。
    }
}
