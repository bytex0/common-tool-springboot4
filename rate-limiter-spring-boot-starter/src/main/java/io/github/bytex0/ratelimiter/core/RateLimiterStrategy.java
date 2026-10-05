package io.github.bytex0.ratelimiter.core;

import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.model.FlowRule;

/**
 * 可注入的限流策略扩展契约，与原策略接口保持相同方法职责。
 *
 * @author bytex0
 * @since 2026-10-05 20:36:23
 */
public interface RateLimiterStrategy {

    /**
     * 返回该策略处理的算法，同一上下文不能声明两个相同类型的自定义策略。
     *
     * @return 非空算法类型
     */
    RateLimiterType getType();

    /**
     * 原子判断并消耗本次许可；实现负责保证并发安全。
     *
     * @param flowRule 已启用的业务规则，调用方不能在执行期间修改
     * @return 是否允许业务执行
     */
    boolean tryAccess(FlowRule flowRule);
}
