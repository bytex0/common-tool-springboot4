package io.github.bytex0.ratelimiter;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.core.RateLimiterFactory;
import io.github.bytex0.ratelimiter.core.RateLimiterStrategy;
import io.github.bytex0.ratelimiter.core.impl.RedisFixedWindowRateLimiterStrategyImpl;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.enums.RedisClientType;
import io.github.bytex0.ratelimiter.exception.RateLimitException;
import io.github.bytex0.ratelimiter.model.FlowRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.beans.Introspector;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 限流(RateLimiterTest)本地原子额度、窗口与配置测试
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
class RateLimiterTest {

    /**
     * 自动配置支持关闭和用户模板覆盖，不主动解析外部客户端。
     */
    @Test
    void shouldConfigureDisableAndOverride() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimiterConfiguration.class));
        runner.run(context -> {
            assertThat(context).hasSingleBean(RateLimiterTemplate.class);
            assertThat(context.getBeansOfType(RateLimiterStrategy.class)).hasSize(RateLimiterType.values().length);
        });
        runner.withPropertyValues("rate-limiter.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RateLimiterTemplate.class));
        RateLimiterTemplate custom = new RateLimiterTemplate(() -> null, 10);
        runner.withBean(RateLimiterTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(RateLimiterTemplate.class)).isSameAs(custom));
    }

    /**
     * 加权许可不会突破本地窗口额度，到期后按新窗口重新计数。
     */
    @Test
    void shouldRespectWeightedQuotaAndResetWindow() {
        AtomicLong clock = new AtomicLong();
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10, clock::get);
        FlowRule weighted = FlowRule.builder().key("key").rateLimiterType(RateLimiterType.LOCAL)
                .maxRequests(3).permits(2).build();
        assertThat(template.tryAccess(weighted)).isTrue();
        assertThat(template.tryAccess(weighted)).isFalse();
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertThat(template.tryAccess(weighted)).isTrue();
    }

    /**
     * 多线程竞争同一业务键时只能得到总额度内的许可。
     *
     * @throws Exception 并发任务执行失败
     */
    @Test
    void shouldApplyConcurrentQuotaAtomically() throws Exception {
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10);
        FlowRule rule = FlowRule.builder().key("key").rateLimiterType(RateLimiterType.LOCAL)
                .maxRequests(3).windowTime(10).build();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(IntStream.range(0, 32)
                    .<Callable<Boolean>>mapToObj(index -> () -> template.tryAccess(rule)).toList());
            long accepted = 0;
            for (var result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(3);
        }
    }

    /**
     * 容量错误释放显式锁，且不能通过驱逐活动键绕过其原额度。
     */
    @Test
    void shouldNotEvictActiveQuotaToAdmitNewKeys() {
        AtomicLong clock = new AtomicLong();
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 1, clock::get);
        assertThat(template.tryAccess(FlowRule.builder().key("a").rateLimiterType(RateLimiterType.LOCAL).build())).isTrue();
        assertThatThrownBy(() -> template.tryAccess(FlowRule.builder().key("b")
                .rateLimiterType(RateLimiterType.LOCAL).build())).isInstanceOf(IllegalStateException.class);
        clock.addAndGet(TimeUnit.MINUTES.toNanos(2));
        assertThat(template.tryAccess(FlowRule.builder().key("b").rateLimiterType(RateLimiterType.LOCAL).build())).isTrue();
    }

    /**
     * 非法规则明确失败，停用规则不要求 Redis 客户端。
     */
    @Test
    void shouldRejectInvalidRulesAndMissingRedis() {
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10);
        assertThatThrownBy(() -> template.tryAccess(FlowRule.builder().key("key").permits(0).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> template.tryAccess(FlowRule.builder().key("key").rateLimiterType(RateLimiterType.REDISSON).build()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(template.tryAccess(FlowRule.builder().enable(false).build())).isTrue();
    }

    /**
     * 保留原模型默认值、无参构造和访问器，Builder 不再丢失默认值。
     */
    @Test
    void shouldRestoreModelAndExceptionContracts() {
        FlowRule rule = new FlowRule();
        assertThat(rule.getRateLimiterType()).isEqualTo(RateLimiterType.REDIS_LUA_SLIDING_WINDOW);
        assertThat(rule.getMaxRequests()).isEqualTo(50);
        assertThat(FlowRule.builder().build().getTokenRate()).isEqualTo(10);
        rule.setRedisClientType(RedisClientType.REDIS_TEMPLATE);
        assertThat(rule.getRedisClientType()).isEqualTo(RedisClientType.REDIS_TEMPLATE);
        rule.setEnable(null);
        assertThat(rule.isEnable()).isFalse();
        FlowRule explicit = new FlowRule(true, RateLimiterType.LOCAL, RedisClientType.REDIS_TEMPLATE,
                "all-arguments", 5, 2, 5, 1, 1);
        assertThat(explicit.getKey()).isEqualTo("all-arguments");
        assertThat(explicit.getMaxRequests()).isEqualTo(5);
        assertThat(explicit.toBuilder().build()).isEqualTo(explicit);
        assertThat(explicit.toBuilder().build().hashCode()).isEqualTo(explicit.hashCode());
        assertThat(explicit.toString()).contains("redisClientType=REDIS_TEMPLATE");
        IllegalStateException cause = new IllegalStateException("cause");
        RateLimitException exception = new RateLimitException("request", "limited", cause);
        assertThat(exception.getRequestId()).isEqualTo("request");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getStackTrace()).isEmpty();
        assertThat(new RateLimitException("quota {}", new Object[]{3}).getMessage()).isEqualTo("quota 3");
        assertThat(new RateLimitException().getMessage()).isNull();
        assertThat(new RateLimitException("limited").getMessage()).isEqualTo("limited");
        assertThat(new RateLimitException(cause).getMessage()).isEqualTo("IllegalStateException: cause");
        assertThat(new RateLimitException("limited", cause).getCause()).isSameAs(cause);
        assertThat(new RateLimitException("request", "limited").getRequestId()).isEqualTo("request");
        assertThat(new RateLimitException(cause, "quota {}", new Object[]{3}).getMessage()).isEqualTo("quota 3");
    }

    /**
     * Guava 保留零预热能力，未使用的窗口容量字段不影响其许可判断。
     */
    @Test
    void shouldAllowGuavaWithoutWarmupOrUnusedWindowFields() {
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10);
        FlowRule rule = FlowRule.builder().key("guava-zero-warmup").rateLimiterType(RateLimiterType.GUAVA)
                .windowTime(0).maxRequests(null).bucketCapacity(null).permits(2).build();
        assertThat(template.tryAccess(rule)).isTrue();
    }

    /**
     * JavaBeans 必须继续识别原 Boolean 属性的读写方法，不能因便捷判断方法变成只读。
     *
     * @throws Exception 属性内省或反射调用失败
     */
    @Test
    void shouldPreserveBooleanBeanProperty() throws Exception {
        var property = Arrays.stream(Introspector.getBeanInfo(FlowRule.class).getPropertyDescriptors())
                .filter(candidate -> "enable".equals(candidate.getName())).findFirst().orElseThrow();
        assertThat(property.getPropertyType()).isEqualTo(Boolean.class);
        assertThat(property.getReadMethod().getName()).isEqualTo("getEnable");
        assertThat(property.getWriteMethod()).isNotNull();
        FlowRule rule = new FlowRule();
        property.getWriteMethod().invoke(rule, false);
        assertThat(rule.getEnable()).isFalse();
        assertThat(rule.isEnable()).isFalse();
    }

    /**
     * 自定义策略由工厂选择，不修改另一个上下文，也不会丢失空规则放行语义。
     */
    @Test
    void shouldRegisterAndIsolateCustomStrategies() {
        RateLimiterStrategy strategy = mock(RateLimiterStrategy.class);
        when(strategy.getType()).thenReturn(RateLimiterType.LOCAL);
        FlowRule rule = FlowRule.builder().key("custom").rateLimiterType(RateLimiterType.LOCAL).build();
        when(strategy.tryAccess(rule)).thenReturn(false);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RateLimiterConfiguration.class))
                .withBean(RateLimiterStrategy.class, () -> strategy).run(context -> {
                    RateLimiterFactory factory = context.getBean(RateLimiterFactory.class);
                    assertThat(factory.tryAccess(rule)).isFalse();
                    assertThat(factory.tryAccess(null)).isTrue();
                });
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10);
        RateLimiterFactory fallback = new RateLimiterFactory(template, List.of());
        fallback.run();
        assertThat(fallback.tryAccess(rule)).isTrue();
        assertThatThrownBy(() -> new RateLimiterFactory(List.of()).tryAccess(rule))
                .isInstanceOf(RateLimitException.class);
        assertThatThrownBy(() -> new RateLimiterFactory(template, List.of(strategy, strategy)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 普通 RedisTemplate 使用 JDK 值序列化器时，脚本仍收到 UTF-8 键和十进制参数。
     */
    @Test
    void shouldExecuteRedisTemplateWithoutResolvingRedisson() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisConnectionFactory connections = mock(RedisConnectionFactory.class);
        RedisScriptingCommands commands = mock(RedisScriptingCommands.class);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.scriptingCommands()).thenReturn(commands);
        when(commands.<Long>eval(any(byte[].class), eq(ReturnType.INTEGER), eq(1), any(byte[][].class)))
                .thenAnswer(invocation -> {
                    byte[][] arguments = (byte[][]) invocation.getRawArguments()[3];
                    assertThat(new String(arguments[0], StandardCharsets.UTF_8)).startsWith("common-tool:rate:");
                    assertThat(new String(arguments[1], StandardCharsets.UTF_8)).isEqualTo("1000");
                    assertThat(new String(arguments[2], StandardCharsets.UTF_8)).isEqualTo("50");
                    return 1L;
                });
        RedisTemplate<String, Object> springTemplate = new RedisTemplate<>();
        springTemplate.setConnectionFactory(connections);
        springTemplate.afterPropertiesSet();
        RateLimiterTemplate template = new RateLimiterTemplate(() -> {
            throw new AssertionError("RedisTemplate must not resolve Redisson");
        }, () -> springTemplate, 10, System::nanoTime);
        assertThat(template.tryAccess(FlowRule.builder().key("中文")
                .redisClientType(RedisClientType.REDIS_TEMPLATE).build())).isTrue();
    }

    /**
     * 原策略的无参子类仍可注册为 Spring Bean，且 getScript 覆盖会真实生效。
     */
    @Test
    void shouldInitializeOriginalNoArgumentSubclass() {
        RateLimiterTemplate template = mock(RateLimiterTemplate.class);
        when(template.tryAccess(any(FlowRule.class), eq("return 1"))).thenReturn(true);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RateLimiterConfiguration.class))
                .withBean(RateLimiterTemplate.class, () -> template)
                .withBean(CustomFixedStrategy.class, CustomFixedStrategy::new)
                .run(context -> assertThat(context.getBean(RateLimiterFactory.class)
                        .tryAccess(FlowRule.builder().key("custom")
                                .rateLimiterType(RateLimiterType.REDIS_LUA_FIXED_WINDOW).build())).isTrue());
    }

    /**
     * 模拟原版本只覆盖脚本的无参扩展策略。
     *
     * @author bytex0
     * @since 2026-10-05 20:46:21
     */
    static class CustomFixedStrategy extends RedisFixedWindowRateLimiterStrategyImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getScript() {
            return "return 1";
        }
    }
}
