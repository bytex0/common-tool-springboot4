package io.github.bytex0.lock.core;

import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.model.LockRule;
import io.github.bytex0.util.ThrowingSupplier;
import io.github.bytex0.lock.core.impl.AbstractTemplateLockStrategy;
import io.github.bytex0.lock.core.impl.ReentrantLockStrategyImpl;
import io.github.bytex0.lock.core.impl.SemaphoreStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonFairLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonSpinLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonSemaphoreLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedisTemplateSemaphoreStrategyImpl;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.util.Assert;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 锁工厂(LockFactory)按上下文固定用户策略，保留独立入口并提供异常安全作用域。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:53:24
 */
public class LockFactory implements CommandLineRunner {

    /**
     * 只有这些精确类型视为内置，用户子类仍可覆盖对应锁策略。
     */
    private static final Set<Class<?>> BUILT_INS = Set.of(ReentrantLockStrategyImpl.class, SemaphoreStrategyImpl.class,
            RedissonLockStrategyImpl.class, RedissonFairLockStrategyImpl.class, RedissonSpinLockStrategyImpl.class,
            RedissonSemaphoreLockStrategyImpl.class, RedisTemplateSemaphoreStrategyImpl.class);

    /**
     * 按构造期输入固定的策略表，无静态全局状态。
     */
    private final Map<LockType, LockStrategy> strategies;

    /**
     * 默认模板；原列表构造器不配置后备实现。
     */
    private final LockTemplate template;

    /**
     * 保留原列表构造器，构造完成即可使用，不依赖启动回调。
     *
     * @param strategies 用户策略列表，类型不得重复
     */
    public LockFactory(List<LockStrategy> strategies) {
        this(null, strategies);
    }

    /**
     * 用户策略优先于后备模板，重复用户类型立即失败。
     *
     * @param template 后备模板，可为空
     * @param strategies 用户策略列表
     */
    public LockFactory(LockTemplate template, List<LockStrategy> strategies) {
        this.template = template;
        Map<LockType, LockStrategy> registry = new EnumMap<>(LockType.class);
        Map<LockType, LockStrategy> custom = new EnumMap<>(LockType.class);
        if (template != null) {
            for (LockType type : LockType.values()) {
                registry.put(type, new DefaultStrategy(template, type));
            }
        }
        for (LockStrategy strategy : strategies) {
            Assert.notNull(strategy.getType(), "锁策略类型不能为空");
            if (BUILT_INS.contains(AopUtils.getTargetClass(strategy))) {
                registry.put(strategy.getType(), strategy);
            } else {
                Assert.isTrue(custom.putIfAbsent(strategy.getType(), strategy) == null, "重复的锁策略");
            }
        }
        registry.putAll(custom);
        this.strategies = Map.copyOf(registry);
    }

    /**
     * 原独立阻塞入口，空规则和关闭规则直接放行。
     *
     * @param rule 锁规则
     */
    public void lock(LockRule rule) {
        if (rule != null && rule.isEnable()) {
            strategy(rule).lock(rule);
        }
    }

    /**
     * 原独立限时入口，空规则和关闭规则直接放行。
     *
     * @param rule 锁规则
     * @return 是否获取成功
     */
    public boolean tryLock(LockRule rule) {
        return rule == null || !rule.isEnable() || strategy(rule).tryLock(rule);
    }

    /**
     * 释放原独立调用中成功取得的锁。
     *
     * @param rule 获取时的规则
     */
    public void unlock(LockRule rule) {
        if (rule != null && rule.isEnable()) {
            strategy(rule).unlock(rule);
        }
    }

    /**
     * 作用域入口同样遵循自定义策略，失败不调用 unlock，保留业务异常与释放异常。
     *
     * @param rule 锁规则，进入时复制
     * @param action 同步业务
     * @param <T> 结果类型
     * @return 业务结果
     * @throws Throwable 业务异常、后端错误或中断
     */
    public <T> T execute(LockRule rule, ThrowingSupplier<T> action) throws Throwable {
        Assert.notNull(action, "业务回调不能为空");
        if (rule == null || !rule.isEnable()) {
            return action.get();
        }
        LockRule snapshot = rule.toBuilder().build();
        LockStrategy strategy = strategies.get(snapshot.getLockType());
        if (strategy == null && template != null) {
            return template.execute(snapshot, action);
        }
        strategy = strategy(snapshot);
        LockRule waiting = snapshot.isBlock() ? snapshot : snapshot.toBuilder().timeout(0L).build();
        if (!strategy.tryLock(waiting)) {
            throw new LockException("未获取锁");
        }
        LockStrategy acquired = strategy;
        try (LockHandle handle = () -> acquired.unlock(waiting)) {
            return action.get();
        }
    }

    /**
     * 解析独立调用策略，未配置时明确失败。
     *
     * @param rule 锁规则
     * @return 对应策略
     */
    private LockStrategy strategy(LockRule rule) {
        LockStrategy strategy = strategies.get(rule.getLockType());
        if (strategy == null) {
            throw new LockException("未找到对应的锁策略类型: " + rule.getLockType());
        }
        return strategy;
    }

    /**
     * 保留原启动入口，注册已在构造时完成。
     *
     * @param args 启动参数，不改变策略表
     */
    @Override
    public void run(String... args) {
        // 构造期注册使应用初始化阶段的调用也能安全使用策略。
    }

    /**
     * 默认策略(DefaultStrategy)为新增读写类型提供与原独立接口一致的所有权管理。
     *
     * @author linshiqiang
     * @since 2026-10-06 01:53:24
     */
    private static final class DefaultStrategy extends AbstractTemplateLockStrategy {

        /**
         * 固定的锁类型。
         */
        private final LockType type;

        /**
         * 为当前类型绑定共享模板。
         *
         * @param template 共享模板
         * @param type 锁类型
         */
        private DefaultStrategy(LockTemplate template, LockType type) {
            super(template, false);
            this.type = type;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LockType getType() {
            return type;
        }
    }
}
