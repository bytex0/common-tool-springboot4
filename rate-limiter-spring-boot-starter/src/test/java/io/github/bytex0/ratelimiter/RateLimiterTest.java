package io.github.bytex0.ratelimiter;

import io.github.bytex0.ratelimiter.core.RateLimiterTemplate;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.model.FlowRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 限流(RateLimiterTest)本地原子额度、窗口与配置测试
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
class RateLimiterTest {

    @Test
    void shouldConfigureDisableAndOverride() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimiterConfiguration.class));
        runner.run(context -> assertThat(context).hasSingleBean(RateLimiterTemplate.class));
        runner.withPropertyValues("rate-limiter.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RateLimiterTemplate.class));
        RateLimiterTemplate custom = new RateLimiterTemplate(() -> null, 10);
        runner.withBean(RateLimiterTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(RateLimiterTemplate.class)).isSameAs(custom));
    }

    @Test
    void shouldRespectWeightedQuotaAndResetWindow() {
        AtomicLong clock = new AtomicLong();
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10, clock::get);
        FlowRule weighted = FlowRule.builder().key("key").maxRequests(3).permits(2).build();
        assertThat(template.tryAccess(weighted)).isTrue();
        assertThat(template.tryAccess(weighted)).isFalse();
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertThat(template.tryAccess(weighted)).isTrue();
    }

    @Test
    void shouldApplyConcurrentQuotaAtomically() throws Exception {
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10);
        FlowRule rule = FlowRule.builder().key("key").maxRequests(3).windowTime(10).build();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(IntStream.range(0, 32)
                    .<Callable<Boolean>>mapToObj(index -> () -> template.tryAccess(rule)).toList());
            long accepted = 0;
            for (var result : results) { if (result.get()) { accepted++; } }
            assertThat(accepted).isEqualTo(3);
        }
    }

    @Test
    void shouldNotEvictActiveQuotaToAdmitNewKeys() {
        AtomicLong clock = new AtomicLong();
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 1, clock::get);
        assertThat(template.tryAccess(FlowRule.builder().key("a").build())).isTrue();
        assertThatThrownBy(() -> template.tryAccess(FlowRule.builder().key("b").build())).isInstanceOf(IllegalStateException.class);
        clock.addAndGet(TimeUnit.MINUTES.toNanos(2));
        assertThat(template.tryAccess(FlowRule.builder().key("b").build())).isTrue();
    }

    @Test
    void shouldRejectInvalidRulesAndMissingRedis() {
        RateLimiterTemplate template = new RateLimiterTemplate(() -> null, 10);
        assertThatThrownBy(() -> template.tryAccess(FlowRule.builder().key("key").permits(0).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> template.tryAccess(FlowRule.builder().key("key").rateLimiterType(RateLimiterType.REDISSON).build()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(template.tryAccess(FlowRule.builder().enable(false).build())).isTrue();
    }
}
