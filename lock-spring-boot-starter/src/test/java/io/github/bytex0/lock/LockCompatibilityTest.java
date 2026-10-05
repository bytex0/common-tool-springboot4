package io.github.bytex0.lock;

import io.github.bytex0.lock.aspect.Lock;
import io.github.bytex0.lock.core.LockFactory;
import io.github.bytex0.lock.core.LockHandle;
import io.github.bytex0.lock.core.LockStrategy;
import io.github.bytex0.lock.core.LockTemplate;
import io.github.bytex0.lock.core.impl.RedissonFairLockStrategyImpl;
import io.github.bytex0.lock.core.impl.RedissonSpinLockStrategyImpl;
import io.github.bytex0.lock.core.impl.SemaphoreStrategyImpl;
import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.enums.RedisClientType;
import io.github.bytex0.lock.exception.LockException;
import io.github.bytex0.lock.model.LockRule;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * 锁兼容测试(LockCompatibilityTest)验证原接口、资源所有权和续租关闭竞争。
 *
 * @author linshiqiang
 * @since 2026-10-06 01:59:22
 */
class LockCompatibilityTest {

    /**
     * 原模型构造、可写属性与注解默认值保持可用。
     *
     * @throws Exception 注解反射失败
     */
    @Test
    void shouldPreserveModelAndAnnotationDefaults() throws Exception {
        LockRule rule = new LockRule();
        assertThat(rule.getLockType()).isEqualTo(LockType.REDISSON_LOCK);
        rule.setKey("mutable");
        rule.setEnable(false);
        assertThat(rule.getEnable()).isFalse();
        assertThat(new LockRule(true, true, LockType.SEMAPHORE, RedisClientType.REDISSON, "key", 2, true,
                3L, TimeUnit.SECONDS).getTimeout()).isEqualTo(3);
        Lock annotation = getClass().getDeclaredMethod("sample").getAnnotation(Lock.class);
        assertThat(annotation.permits()).isEqualTo(10);
        assertThat(annotation.key()).isEqualTo("defaultLockKey");
        assertThat(annotation.redisClientType()).isEqualTo(RedisClientType.REDISSON);
    }

    /**
     * 原异常重载保留占位符、请求标识和原因，不采集业务堆栈。
     */
    @Test
    void shouldPreserveExceptionConstructors() {
        Throwable cause = new IllegalStateException("cause");
        assertThat(new LockException().getRequestId()).isNull();
        assertThat(new LockException("id", "message").getRequestId()).isEqualTo("id");
        assertThat(new LockException("id", "message", cause).getCause()).isSameAs(cause);
        assertThat(new LockException("value={}", 3).getMessage()).isEqualTo("value=3");
        assertThat(new LockException(cause, "value={}", 3).getMessage()).isEqualTo("value=3");
        assertThat(new LockException(cause).getMessage()).isEqualTo("IllegalStateException: cause");
        assertThat(new LockException("message", cause).getStackTrace()).isEmpty();
    }

    /**
     * 原独立策略在构造后即可使用，未获取的其他线程不会增加信号量额度。
     *
     * @throws Exception 跨线程验证失败
     */
    @Test
    void shouldRestoreStandaloneStrategyAndFactory() throws Exception {
        try (SemaphoreStrategyImpl strategy = new SemaphoreStrategyImpl();
             var worker = Executors.newSingleThreadExecutor()) {
            LockFactory factory = new LockFactory(List.of(strategy));
            LockRule rule = LockRule.builder().key("key").lockType(LockType.SEMAPHORE).timeout(0L).build();
            assertThat(factory.tryLock(rule)).isTrue();
            worker.submit(() -> {
                assertThat(factory.tryLock(rule)).isFalse();
                factory.unlock(rule);
                assertThat(factory.tryLock(rule)).isFalse();
            }).get(5, TimeUnit.SECONDS);
            factory.unlock(rule);
            assertThat(factory.tryLock(rule)).isTrue();
            factory.unlock(rule);
            assertThat(factory.tryLock(null)).isTrue();
            factory.lock(null);
            factory.unlock(null);
        }
    }

    /**
     * 自定义策略优先于默认 Bean，工厂失败获取不会调用释放。
     */
    @Test
    void shouldUseCustomStrategyAndNotUnlockOnRejection() {
        LockStrategy custom = mock(LockStrategy.class);
        when(custom.getType()).thenReturn(LockType.REDISSON_LOCK);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(LockConfiguration.class))
                .withBean("customLock", LockStrategy.class, () -> custom)
                .run(context -> {
                    LockFactory factory = context.getBean(LockFactory.class);
                    assertThatThrownBy(() -> factory.execute(LockRule.builder().key("key").build(), () -> "no"))
                            .isInstanceOf(LockException.class);
                    verify(custom, never()).unlock(any());
                    assertThat(context).hasSingleBean(RedissonFairLockStrategyImpl.class);
                });
    }

    /**
     * 关闭模板不清空在途本地锁，容量和关闭校验均发生在业务之前。
     *
     * @throws Exception 锁获取失败
     */
    @Test
    void shouldKeepInflightOwnershipWhenClosing() throws Exception {
        try (LockTemplate template = new LockTemplate(() -> null, () -> null, 1)) {
            LockRule rule = localRule();
            LockHandle handle = template.acquire(rule, false);
            assertThatThrownBy(() -> template.acquire(rule, false)).hasMessageContaining("容量");
            template.close();
            assertThat(template.activeLocalKeys()).isEqualTo(1);
            assertThatThrownBy(() -> template.acquire(rule, false)).hasMessageContaining("关闭");
            handle.close();
            handle.close();
            assertThat(template.activeLocalKeys()).isZero();
        }
    }

    /**
     * 外部规则修改不影响已取得的资源，跨线程关闭不会消耗原所有者的释放机会。
     *
     * @throws Exception 跨线程验证失败
     */
    @Test
    void shouldSnapshotRuleAndRejectWrongThreadRelease() throws Exception {
        try (LockTemplate template = new LockTemplate(() -> null);
             var worker = Executors.newSingleThreadExecutor()) {
            LockRule rule = localRule();
            LockHandle handle = template.acquire(rule, false);
            rule.setKey("changed");
            worker.submit(() -> assertThatThrownBy(handle::close).hasMessageContaining("获取线程"))
                    .get(5, TimeUnit.SECONDS);
            handle.close();
            assertThat(template.activeLocalKeys()).isZero();
        }
    }

    /**
     * 公平策略必须调用公平锁，自旋策略必须调用自旋锁。
     *
     * @throws Exception 后端模拟异常
     */
    @Test
    void shouldSelectCorrectRedissonLocks() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getFairLock(anyString())).thenReturn(lock);
        when(client.getSpinLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        LockRule rule = LockRule.builder().key("fair").timeout(0L).build();
        try (var fair = new RedissonFairLockStrategyImpl(client);
             var spin = new RedissonSpinLockStrategyImpl(client)) {
            assertThat(fair.tryLock(rule)).isTrue();
            fair.unlock(rule);
            assertThat(spin.tryLock(rule)).isTrue();
            spin.unlock(rule);
            verify(client).getFairLock(anyString());
            verify(client).getSpinLock(anyString());
        }
        verify(client, never()).shutdown();
    }

    /**
     * 正纳秒租约不能截断为无限 watchdog。
     *
     * @throws Throwable 作用域失败
     */
    @Test
    void shouldRoundPositiveLeaseUp() throws Throwable {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(0, 1, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        try (LockTemplate template = new LockTemplate(() -> client)) {
            template.execute(LockRule.builder().key("key").timeout(0L).leaseTime(1).timeUnit(TimeUnit.NANOSECONDS).build(),
                    () -> "ok");
            verify(lock).tryLock(0, 1, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 模板关闭后仍为在途信号量续租，不使持有者无声失去互斥。
     *
     * @throws Exception 线程协调失败
     */
    @Test
    void shouldKeepRenewingInflightPermitAfterClose() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RPermitExpirableSemaphore semaphore = semaphore(client);
        CountDownLatch renewed = new CountDownLatch(2);
        when(semaphore.updateLeaseTime("token", 1000, TimeUnit.MILLISECONDS)).thenAnswer(call -> {
            renewed.countDown();
            return true;
        });
        try (LockTemplate template = new LockTemplate(() -> client)) {
            LockHandle handle = template.acquire(semaphoreRule(), false);
            template.close();
            assertThat(renewed.await(5, TimeUnit.SECONDS)).isTrue();
            handle.close();
            verify(semaphore).tryRelease("token");
        }
    }

    /**
     * 释放不等待正在进行的网络续租，迟到的失败结果不能把已关闭句柄标为丢失。
     *
     * @throws Exception 线程协调失败
     */
    @Test
    void shouldAllowReleaseDuringRenewal() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RPermitExpirableSemaphore semaphore = semaphore(client);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(semaphore.updateLeaseTime("token", 1000, TimeUnit.MILLISECONDS)).thenAnswer(call -> {
            entered.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            return false;
        });
        try (LockTemplate template = new LockTemplate(() -> client)) {
            LockHandle handle = template.acquire(semaphoreRule(), false);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                handle.close();
                verify(semaphore).tryRelease("token");
            } finally {
                finish.countDown();
            }
        }
    }

    /**
     * 只提供 RedisTemplate 时可执行模板信号量，不能暗中回退到 Redisson。
     *
     * @throws Throwable 作用域失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldUseRealTemplateBackendWithoutRedisson() throws Throwable {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);
        try (LockTemplate template = new LockTemplate(() -> null, () -> redisTemplate, 10)) {
            LockRule rule = LockRule.builder().key("key").lockType(LockType.REDIS_TEMPLATE_SEMAPHORE).build();
            assertThat(template.execute(rule, () -> "ok")).isEqualTo("ok");
            LockRule alias = rule.toBuilder().lockType(LockType.REDISSON_SEMAPHORE)
                    .redisClientType(RedisClientType.REDIS_TEMPLATE).build();
            assertThat(template.execute(alias, () -> "alias")).isEqualTo("alias");
            verify(redisTemplate, times(4)).execute(any(RedisCallback.class));
        }
    }

    /**
     * 错误配置在尝试外部连接之前失败。
     */
    @Test
    void shouldRejectInvalidRulesAndDuplicateStrategies() {
        try (LockTemplate template = new LockTemplate(() -> null)) {
            assertThatThrownBy(() -> template.execute(LockRule.builder().key("key").permits(null).build(), () -> "no"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> template.execute(LockRule.builder().key("key")
                    .redisClientType(RedisClientType.REDIS_TEMPLATE).build(), () -> "no"))
                    .hasMessageContaining("仅支持Redisson");
        }
        LockStrategy custom = mock(LockStrategy.class);
        when(custom.getType()).thenReturn(LockType.REENTRANT_LOCK);
        assertThatThrownBy(() -> new LockFactory(List.of(custom, custom))).hasMessageContaining("重复");
    }

    /**
     * 自定义策略业务失败仍会释放，并保留原异常。
     */
    @Test
    void shouldReleaseCustomStrategyAfterBusinessFailure() {
        LockStrategy custom = mock(LockStrategy.class);
        when(custom.getType()).thenReturn(LockType.REENTRANT_LOCK);
        when(custom.tryLock(any())).thenReturn(true);
        LockFactory factory = new LockFactory(List.of(custom));
        assertThatThrownBy(() -> factory.execute(localRule(), () -> {
            throw new IllegalArgumentException("business");
        })).hasMessage("business");
        verify(custom).unlock(any());
    }

    /**
     * 生成一个本地互斥测试规则。
     *
     * @return 测试规则
     */
    private LockRule localRule() {
        return LockRule.builder().key("key").lockType(LockType.REENTRANT_LOCK).build();
    }

    /**
     * 生成一秒续租测试规则。
     *
     * @return 测试规则
     */
    private LockRule semaphoreRule() {
        return LockRule.builder().key("key").lockType(LockType.REDISSON_SEMAPHORE).leaseTime(1000).timeout(0L).build();
    }

    /**
     * 建立唯一凭证的外部存储边界替身。
     *
     * @param client 客户端替身
     * @return 信号量替身
     * @throws Exception 后端方法受检异常
     */
    private RPermitExpirableSemaphore semaphore(RedissonClient client) throws Exception {
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        when(client.getPermitExpirableSemaphore(anyString())).thenReturn(semaphore);
        when(semaphore.getPermits()).thenReturn(1);
        when(semaphore.tryAcquire(0, 1000, TimeUnit.MILLISECONDS)).thenReturn("token");
        when(semaphore.tryRelease("token")).thenReturn(true);
        return semaphore;
    }

    /**
     * 用于读取原注解默认值。
     */
    @Lock
    private void sample() {
    }
}
