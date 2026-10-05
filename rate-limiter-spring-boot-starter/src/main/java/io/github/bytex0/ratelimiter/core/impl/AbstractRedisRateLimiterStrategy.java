package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.model.FlowRule;
import org.springframework.util.Assert;

/**
 * Redis Lua 策略基类，保留自定义脚本及显式脚本参数扩展入口。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public abstract class AbstractRedisRateLimiterStrategy extends AbstractTemplateRateLimiterStrategy {

    /**
     * 保留原子类隐式 super() 构造方式，由其所属 Spring 容器注入模板。
     */
    protected AbstractRedisRateLimiterStrategy() {
        super();
    }

    /**
     * 构造双后端 Lua 策略。
     *
     * @param template 当前上下文的模板
     */
    protected AbstractRedisRateLimiterStrategy(RateLimiterTemplate template) {
        super(template);
    }

    /**
     * 按标准规则协议执行当前策略脚本，子类覆盖 getScript 时同样生效。
     *
     * @param flowRule 非空业务规则
     * @return 是否获得许可
     */
    @Override
    public boolean tryAccess(FlowRule flowRule) {
        Assert.notNull(flowRule, "限流规则不能为空");
        if (!Boolean.TRUE.equals(flowRule.getEnable())) {
            return true;
        }
        return template().tryAccess(flowRule.toBuilder().rateLimiterType(getType()).build(), getScript());
    }

    /**
     * 执行扩展脚本，不改写调用方指定的脚本键或参数。
     *
     * @param flowRule 后端和脚本键来源，不能为 null
     * @param values 与 getScript 所返回脚本匹配的标量参数
     * @return 脚本返回 1 时允许访问
     */
    public boolean tryAccess(FlowRule flowRule, Object... values) {
        Assert.notNull(flowRule, "限流规则不能为空");
        if (!Boolean.TRUE.equals(flowRule.getEnable())) {
            return true;
        }
        return template().executeScript(getScript(), flowRule.getRedisClientType(), flowRule.getKey(), values);
    }

    /**
     * 返回可信 Lua 脚本，使用 KEYS[1] 和传入的 ARGV，结果须为整数许可标志。
     *
     * @return Lua 脚本内容
     */
    public abstract String getScript();
}
