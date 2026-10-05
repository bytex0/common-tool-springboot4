package io.github.bytex0.lock;

import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.model.LockRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 锁执行(LockTest)所有权、异常、中断及本地引用清理测试
 *
 * @author linshiqiang
 * @since 2026-10-05 16:36:32
 */
class LockTest {

    @Test
    void shouldConfigureWithoutConnectingAndRespectDisable() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LockConfiguration.class));
        runner.run(context -> assertThat(context).hasSingleBean(LockTemplate.class));
        runner.withPropertyValues("lock.enabled=false").run(context -> assertThat(context).doesNotHaveBean(LockTemplate.class));
        LockTemplate custom = new LockTemplate(() -> null);
        runner.withBean(LockTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(LockTemplate.class)).isSameAs(custom));
    }

    @Test
    void shouldReleaseAfterBusinessFailureAndRemoveIdleKeys() throws Throwable {
        try (LockTemplate template = new LockTemplate(() -> null)) {
            LockRule rule = LockRule.builder().key("key").build();
            assertThatThrownBy(() -> template.execute(rule, () -> { throw new IllegalStateException("business"); }))
                    .hasMessage("business");
            assertThat(template.activeLocalKeys()).isZero();
            assertThat(template.execute(rule, () -> "ok")).isEqualTo("ok");
        }
    }

    @Test
    void shouldNotReleaseSemaphoreWhenAcquireFailed() throws Exception {
        try (LockTemplate template = new LockTemplate(() -> null); var executor = Executors.newSingleThreadExecutor()) {
            LockRule rule = LockRule.builder().key("key").lockType(LockType.SEMAPHORE).block(false).build();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var held = executor.submit(() -> {
                try {
                    template.execute(rule, () -> { entered.countDown(); release.await(); return null; });
                } catch (Throwable exception) { throw new RuntimeException(exception); }
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                for (int attempt = 0; attempt < 3; attempt++) {
                    assertThatThrownBy(() -> template.execute(rule, () -> "must-not-run")).isInstanceOf(LockException.class);
                }
            } finally {
                release.countDown();
            }
            held.get(5, TimeUnit.SECONDS);
            assertThat(template.activeLocalKeys()).isZero();
        }
    }

    @Test
    void shouldRestoreInterruptedStatusAndRejectMissingRedis() {
        try (LockTemplate template = new LockTemplate(() -> null)) {
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> template.execute(LockRule.builder().key("key").build(), () -> null))
                        .isInstanceOf(InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            assertThat(template.activeLocalKeys()).isZero();
            assertThatThrownBy(() -> template.execute(LockRule.builder().key("key").lockType(LockType.REDISSON_LOCK).build(), () -> null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
