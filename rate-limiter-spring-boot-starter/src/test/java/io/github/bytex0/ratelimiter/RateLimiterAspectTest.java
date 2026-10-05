package io.github.bytex0.ratelimiter;

import io.github.bytex0.ratelimiter.aspect.RateLimiter;
import io.github.bytex0.ratelimiter.aspect.RateLimiterAspect;
import io.github.bytex0.ratelimiter.core.RateLimiterFactory;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.enums.RedisClientType;
import io.github.bytex0.ratelimiter.exception.RateLimitException;
import io.github.bytex0.ratelimiter.model.FlowRule;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 使用实际 Spring AOP 代理验证注解属性、表达式覆盖和业务调用边界。
 *
 * @author bytex0
 * @since 2026-10-05 20:46:21
 */
class RateLimiterAspectTest {

    /**
     * 验证所有数值表达式和 Redis 后端属性都到达策略工厂。
     */
    @Test
    void shouldResolveAnnotationFields() {
        RateLimiterFactory factory = mock(RateLimiterFactory.class);
        when(factory.tryAccess(any())).thenReturn(true);
        Business target = new Business();
        Business proxy = proxy(target, factory);
        assertThat(proxy.fields("order", 7)).isEqualTo(1);
        ArgumentCaptor<FlowRule> captured = ArgumentCaptor.forClass(FlowRule.class);
        verify(factory).tryAccess(captured.capture());
        FlowRule rule = captured.getValue();
        assertThat(rule.getKey()).isEqualTo("el:order");
        assertThat(rule.getRedisClientType()).isEqualTo(RedisClientType.REDIS_TEMPLATE);
        assertThat(rule.getRateLimiterType()).isEqualTo(RateLimiterType.REDIS_LUA_TOKEN_BUCKET);
        assertThat(rule.getMaxRequests()).isEqualTo(7);
        assertThat(rule.getWindowTime()).isEqualTo(2);
        assertThat(rule.getBucketCapacity()).isEqualTo(20);
        assertThat(rule.getTokenRate()).isEqualTo(3);
        assertThat(rule.getPermits()).isEqualTo(2);
    }

    /**
     * 完整规则覆盖其他属性，字段表达式返回 null 时则使用常量默认值。
     */
    @Test
    void shouldRespectCompleteRuleAndNullFieldFallback() {
        RateLimiterFactory factory = mock(RateLimiterFactory.class);
        when(factory.tryAccess(any())).thenReturn(true);
        Business proxy = proxy(new Business(), factory);
        proxy.complete("invoice");
        ArgumentCaptor<FlowRule> captured = ArgumentCaptor.forClass(FlowRule.class);
        verify(factory).tryAccess(captured.capture());
        assertThat(captured.getValue().getKey()).isEqualTo("provided:invoice");
        assertThat(captured.getValue().getMaxRequests()).isEqualTo(23);
        assertThat(captured.getValue().getRateLimiterType()).isEqualTo(RateLimiterType.LOCAL);

        RateLimiterFactory fallbackFactory = mock(RateLimiterFactory.class);
        when(fallbackFactory.tryAccess(any())).thenReturn(true);
        proxy(new Business(), fallbackFactory).fields("fallback", null);
        verify(fallbackFactory).tryAccess(captured.capture());
        assertThat(captured.getValue().getMaxRequests()).isEqualTo(10);
    }

    /**
     * 禁用时不求值表达式，额度不足时不调用业务。
     */
    @Test
    void shouldSkipDisabledAndRejectBeforeBusiness() {
        RateLimiterFactory factory = mock(RateLimiterFactory.class);
        Business target = new Business();
        Business proxy = proxy(target, factory);
        assertThat(proxy.disabled()).isEqualTo(1);
        verifyNoInteractions(factory);
        assertThatThrownBy(() -> proxy.fields("rejected", 1)).isInstanceOf(RateLimitException.class);
        assertThat(target.calls.get()).isEqualTo(1);
    }

    /**
     * 保留原示例中通过参数名访问 DTO 属性的 SpEL 用法。
     */
    @Test
    void shouldResolveOriginalDtoPropertyExpression() {
        RateLimiterFactory factory = mock(RateLimiterFactory.class);
        when(factory.tryAccess(any())).thenReturn(true);
        proxy(new Business(), factory).nested(new Payload("001"));
        ArgumentCaptor<FlowRule> captured = ArgumentCaptor.forClass(FlowRule.class);
        verify(factory).tryAccess(captured.capture());
        assertThat(captured.getValue().getKey()).isEqualTo("dto:001");
    }

    /**
     * 创建真实切面代理并注册规则提供器，不依赖外部 Redis。
     *
     * @param target 测试业务对象
     * @param factory 额度判断边界
     * @return Spring 代理
     */
    private Business proxy(Business target, RateLimiterFactory factory) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("rules", new Rules());
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new RateLimiterAspect(factory, beans));
        return proxyFactory.getProxy();
    }

    /**
     * 可被 SpEL 调用的确定性规则提供器。
     *
     * @author bytex0
     * @since 2026-10-05 20:46:21
     */
    public static class Rules {

        /**
         * 返回完整规则以验证注解字段被覆盖。
         *
         * @param key 业务参数
         * @return 本地固定窗口规则
         */
        public FlowRule rule(String key) {
            FlowRule rule = new FlowRule();
            rule.setKey("provided:" + key);
            rule.setRateLimiterType(RateLimiterType.LOCAL);
            rule.setMaxRequests(23);
            return rule;
        }
    }

    /**
     * 与原示例属性访问方式一致的测试 DTO。
     *
     * @author bytex0
     * @since 2026-10-05 20:46:21
     */
    public static class Payload {

        /**
         * 用于区分业务额度的编码。
         */
        private final String code;

        /**
         * 构造测试 DTO。
         *
         * @param code 业务编码
         */
        public Payload(String code) {
            this.code = code;
        }

        /**
         * 返回可供 SpEL 读取的业务编码。
         *
         * @return 业务编码
         */
        public String getCode() {
            return code;
        }
    }

    /**
     * 记录业务是否真正执行的测试服务。
     *
     * @author bytex0
     * @since 2026-10-05 20:46:21
     */
    public static class Business {

        /**
         * 原始目标对象上的业务调用次数。
         */
        private final AtomicInteger calls = new AtomicInteger();

        /**
         * 使用全部字段表达式的测试入口。
         *
         * @param key 业务键
         * @param maximum 最大许可数，允许为空以测试回退
         * @return 当前业务调用次数
         */
        @RateLimiter(type = RateLimiterType.REDIS_LUA_TOKEN_BUCKET, redisClientType = RedisClientType.REDIS_TEMPLATE,
                key = "'el:' + #p0", maxRequestsFunction = "#p1", windowTimeFunction = "2",
                bucketCapacityFunction = "20", tokenRateFunction = "3", permitsFunction = "2")
        public int fields(String key, Integer maximum) {
            return calls.incrementAndGet();
        }

        /**
         * 使用完整规则提供器，其他字段中的非法表达式不应被求值。
         *
         * @param key 业务键
         * @return 当前业务调用次数
         */
        @RateLimiter(ruleFunction = "@rules.rule(#key)", key = "invalid expression", maxRequestsFunction = "invalid")
        public int complete(String key) {
            return calls.incrementAndGet();
        }

        /**
         * 使用原示例的参数名和 DTO 属性访问语法。
         *
         * @param data 包含业务编码的参数
         * @return 当前业务调用次数
         */
        @RateLimiter(key = "'dto:' + #data.code")
        public int nested(Payload data) {
            return calls.incrementAndGet();
        }

        /**
         * 禁用限流后忽略所有规则表达式。
         *
         * @return 当前业务调用次数
         */
        @RateLimiter(enable = false, key = "invalid expression", ruleFunction = "invalid expression")
        public int disabled() {
            return calls.incrementAndGet();
        }
    }
}
