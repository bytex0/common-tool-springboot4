package io.github.bytex0.idempotent;

import io.github.bytex0.idempotent.core.IdempotentKeyGenerator;
import io.github.bytex0.idempotent.core.RedisIdempotentExecutor;
import io.github.bytex0.idempotent.exception.IdempotentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等(IdempotentTest)稳定键、成功窗口及失败释放测试
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
class IdempotentTest {

    /**
     * 测试Redis边界
     */
    private RedissonClient client;

    /**
     * 处理锁替身
     */
    private RLock lock;

    /**
     * 成功标记替身
     */
    private RBucket<String> completed;

    /**
     * 被测执行器
     */
    private RedisIdempotentExecutor executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws InterruptedException {
        client = mock(RedissonClient.class);
        lock = mock(RLock.class);
        completed = mock(RBucket.class);
        when(client.<String>getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(completed);
        when(client.getLock(anyString())).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(false, true);
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenReturn(true);
        executor = new RedisIdempotentExecutor(() -> client);
    }

    @Test
    void shouldConfigureDisableAndOverrideWithoutConnecting() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempotentConfiguration.class));
        runner.run(context -> assertThat(context).hasSingleBean(RedisIdempotentExecutor.class));
        runner.withPropertyValues("idempotent.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RedisIdempotentExecutor.class));
        runner.withBean(RedisIdempotentExecutor.class, () -> executor)
                .run(context -> assertThat(context.getBean(RedisIdempotentExecutor.class)).isSameAs(executor));
    }

    @Test
    void shouldUseCanonicalParametersAndNamespace() throws Exception {
        IdempotentKeyGenerator keys = new IdempotentKeyGenerator(new DefaultListableBeanFactory());
        var method = getClass().getDeclaredMethod("operation", Object.class);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2); first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1); second.put("b", 2);
        String key = keys.generateKey("", "scope", this, method, new Object[]{first});
        assertThat(keys.generateKey("", "scope", this, method, new Object[]{second})).isEqualTo(key);
        assertThat(keys.generateKey("", "other", this, method, new Object[]{first})).isNotEqualTo(key);
        assertThat(keys.generateKey("#p0", "scope", this, method, new Object[]{"stable"})).hasSize(64);
        assertThatThrownBy(() -> keys.generateKey("#p0", "scope", this, method, new Object[]{new Object()}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMarkOnlyAfterSuccessfulActionAndUnlock() throws Throwable {
        assertThat(executor.execute("key", Duration.ofSeconds(2), () -> "ok")).isEqualTo("ok");
        verify(completed).set("done", Duration.ofSeconds(2));
        verify(lock).tryLock(0, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void shouldNotMarkFailureAndShouldReleaseForRetry() {
        assertThatThrownBy(() -> executor.execute("key", Duration.ofSeconds(2), () -> {
            throw new IllegalStateException("business failure");
        })).hasMessage("business failure");
        verify(completed, never()).set(anyString(), any(Duration.class));
        verify(lock).unlock();
    }

    @Test
    void shouldRejectCompletedRequestsWithoutRunningBusiness() {
        when(completed.isExists()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute("key", Duration.ofSeconds(1), calls::incrementAndGet))
                .isInstanceOf(IdempotentException.class);
        assertThat(calls).hasValue(0);
        verify(client, never()).getLock(anyString());
    }

    @Test
    void shouldNeverUnlockOnRejectedAcquisitionOrNestedInvocation() throws InterruptedException {
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenReturn(false);
        assertThatThrownBy(() -> executor.execute("key", Duration.ofSeconds(1), () -> "no"))
                .isInstanceOf(IdempotentException.class);
        verify(lock, never()).unlock();
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        assertThatThrownBy(() -> executor.execute("key", Duration.ofSeconds(1), () -> "no"))
                .isInstanceOf(IdempotentException.class);
        verify(lock, never()).unlock();
    }

    public void operation(Object request) {
    }
}
