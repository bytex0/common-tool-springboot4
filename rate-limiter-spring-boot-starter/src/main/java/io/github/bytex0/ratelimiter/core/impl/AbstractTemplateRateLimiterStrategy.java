package io.github.bytex0.ratelimiter.core.impl;

import io.github.bytex0.ratelimiter.core.RateLimiterStrategy;
import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.model.FlowRule;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.util.Assert;

/**
 * 具体策略共享模板的状态和校验，不修改调用方提供的规则。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
public abstract class AbstractTemplateRateLimiterStrategy implements RateLimiterStrategy, BeanFactoryAware {

    /**
     * 当前上下文共享的算法实现，只允许初始化一次，不使用静态应用上下文。
     */
    private final AtomicReference<RateLimiterTemplate> templateReference = new AtomicReference<>();

    /**
     * 支持原无参策略及其子类由 Spring 创建，模板在 BeanFactoryAware 回调中绑定。
     */
    protected AbstractTemplateRateLimiterStrategy() {
    }

    /**
     * 构造具体策略。
     *
     * @param template 当前上下文的模板
     */
    protected AbstractTemplateRateLimiterStrategy(RateLimiterTemplate template) {
        Assert.notNull(template, "限流模板不能为空");
        templateReference.set(template);
    }

    /**
     * 仅为原无参构造路径绑定当前容器的模板，不替换显式传入的模板。
     *
     * @param beanFactory 创建当前策略的容器
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (templateReference.get() == null) {
            templateReference.compareAndSet(null, beanFactory.getBean(RateLimiterTemplate.class));
        }
    }

    /**
     * 获取已绑定模板，手工创建的 Redis 策略应使用显式模板构造器。
     *
     * @return 当前策略的模板
     * @throws IllegalStateException 无参策略尚未由容器初始化
     */
    protected RateLimiterTemplate template() {
        RateLimiterTemplate template = templateReference.get();
        Assert.state(template != null, "无参策略尚未由Spring初始化，手工创建时请传入限流模板");
        return template;
    }

    /**
     * 按具体策略类型执行规则，复制规则以防止修改调用方的算法字段。
     *
     * @param flowRule 非空业务规则
     * @return 是否获得许可
     */
    @Override
    public boolean tryAccess(FlowRule flowRule) {
        Assert.notNull(flowRule, "限流规则不能为空");
        return template().tryAccess(flowRule.toBuilder().rateLimiterType(getType()).build());
    }
}
