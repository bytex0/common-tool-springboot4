package io.github.bytex0.idempotent;

import io.github.bytex0.idempotent.core.IdempotentKeyGenerator;
import io.github.bytex0.idempotent.core.RedisIdempotentExecutor;
import io.github.bytex0.idempotent.exception.IdempotentException;
import io.github.bytex0.idempotent.config.IdempotentProperties;
import io.github.bytex0.idempotent.aspect.Idempotent;
import io.github.bytex0.idempotent.aspect.IdempotentAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
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

    /**
     * 构造存储边界替身，不需要真实 Redis。
     *
     * @throws InterruptedException 模拟获取处理锁的受检异常
     */
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

    /**
     * 默认注册、关闭开关和用户执行器覆盖均不连接 Redis。
     */
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

    /**
     * 默认摘要与 Map 插入顺序无关，且不同命名空间互相隔离。
     *
     * @throws Exception 反射样例签名读取失败
     */
    @Test
    void shouldUseCanonicalParametersAndNamespace() throws Exception {
        IdempotentKeyGenerator keys = new IdempotentKeyGenerator(new DefaultListableBeanFactory());
        var method = getClass().getDeclaredMethod("operation", Object.class);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);
        String key = keys.generateKey("", "scope", this, method, new Object[]{first});
        assertThat(keys.generateKey("", "scope", this, method, new Object[]{second})).isEqualTo(key);
        assertThat(keys.generateKey("", "other", this, method, new Object[]{first})).isNotEqualTo(key);
        assertThat(keys.generateKey("#p0", "scope", this, method, new Object[]{"stable"})).hasSize(64);
        assertThatThrownBy(() -> keys.generateKey("#p0", "scope", this, method, new Object[]{new Object()}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 成功业务写入完成窗口且释放自己取得的处理锁。
     *
     * @throws Throwable 作用域业务失败
     */
    @Test
    void shouldMarkOnlyAfterSuccessfulActionAndUnlock() throws Throwable {
        assertThat(executor.execute("key", Duration.ofSeconds(2), () -> "ok")).isEqualTo("ok");
        verify(completed).set("done", Duration.ofSeconds(2));
        verify(lock).tryLock(0, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    /**
     * 业务失败不占用完成窗口，保持原异常并释放锁。
     */
    @Test
    void shouldNotMarkFailureAndShouldReleaseForRetry() {
        assertThatThrownBy(() -> executor.execute("key", Duration.ofSeconds(2), () -> {
            throw new IllegalStateException("business failure");
        })).hasMessage("business failure");
        verify(completed, never()).set(anyString(), any(Duration.class));
        verify(lock).unlock();
    }

    /**
     * 已完成的请求不再执行任何业务或尝试取得锁。
     */
    @Test
    void shouldRejectCompletedRequestsWithoutRunningBusiness() {
        when(completed.isExists()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute("key", Duration.ofSeconds(1), calls::incrementAndGet))
                .isInstanceOf(IdempotentException.class);
        assertThat(calls).hasValue(0);
        verify(client, never()).getLock(anyString());
    }

    /**
     * 竞争失败或同线程嵌套均不释放其他作用域的锁。
     *
     * @throws InterruptedException 模拟锁获取失败
     */
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

    /**
     * 原独立检查使用配置窗口并同时写入新旧完成标记。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldRestoreStandaloneCheckAndDefaultWindow() {
        IdempotentProperties properties = new IdempotentProperties();
        properties.setDefaultExpireSeconds(Duration.ofSeconds(7));
        RBucket<Object> original = mock(RBucket.class);
        when(client.getBucket("legacy")).thenReturn(original);
        when(original.setIfAbsent(anyLong(), eq(Duration.ofSeconds(7)))).thenReturn(true);
        new RedisIdempotentExecutor(client, properties).execute("legacy", 0);
        verify(original).setIfAbsent(anyLong(), eq(Duration.ofSeconds(7)));
        verify(completed).set("legacy", Duration.ofSeconds(7));
        verify(lock).unlock();
    }

    /**
     * 原独立检查发现原始键已存在时，不覆盖原窗口。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectExistingLegacyReservation() {
        RBucket<Object> original = mock(RBucket.class);
        when(client.getBucket("legacy")).thenReturn(original);
        assertThatThrownBy(() -> executor.execute("legacy", 2)).isInstanceOf(IdempotentException.class);
        verify(completed, never()).set(anyString(), any(Duration.class));
        verify(lock).unlock();
    }

    /**
     * 新作用域完成后，原独立检查不重复放行。
     */
    @Test
    void shouldRejectStandaloneCheckAfterScopedCompletion() {
        when(completed.isExists()).thenReturn(true);
        assertThatThrownBy(() -> executor.execute("legacy", 2)).isInstanceOf(IdempotentException.class);
        verify(client, never()).getBucket("legacy");
        verify(lock).unlock();
    }

    /**
     * 不合法参数及缺失客户端在执行回调前明确失败。
     */
    @Test
    void shouldValidateBeforeConnecting() {
        RedisIdempotentExecutor missing = new RedisIdempotentExecutor(() -> null);
        assertThatThrownBy(() -> missing.execute("", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> missing.execute("key", Duration.ZERO, () -> "no"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> missing.execute("key", 1)).hasMessageContaining("RedissonClient");
    }

    /**
     * 获取锁被中断时恢复中断状态，不释放未取得的锁。
     *
     * @throws InterruptedException 模拟锁中断
     */
    @Test
    void shouldPreserveInterruptedAcquisition() throws InterruptedException {
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException("cancel"));
        try {
            assertThatThrownBy(() -> executor.execute("key", 1))
                    .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 丢失所有权时不写成功标记、不误释放新持有者的锁。
     */
    @Test
    void shouldRejectLostOwnership() {
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        assertThatThrownBy(() -> executor.execute("key", Duration.ofSeconds(1), () -> "ok"))
                .isInstanceOf(IdempotentException.class).hasMessageContaining("丢失");
        verify(completed, never()).set(anyString(), any(Duration.class));
        verify(lock, never()).unlock();
    }

    /**
     * 原表达式、参数名拼接及 Bean 引用继续产生已知 MD5 协议。
     *
     * @throws Exception 反射读取样例方法失败
     */
    @Test
    void shouldPreserveLegacyKeyProtocol() throws Exception {
        IdempotentKeyGenerator keys = new IdempotentKeyGenerator();
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("greeting", "hello");
        BeanFactoryResolver resolver = new BeanFactoryResolver(beans);
        ProceedingJoinPoint point = point("hello");
        assertThat(keys.generateKey("#request", "original:", point, resolver))
                .isEqualTo("original:5d41402abc4b2a76b9719d911017c592");
        assertThat(keys.generateKey("@greeting", "original:", point, resolver))
                .isEqualTo("original:5d41402abc4b2a76b9719d911017c592");
        MethodSignature signature = (MethodSignature) point.getSignature();
        when(signature.getParameterNames()).thenReturn(new String[]{"hel"});
        when(point.getArgs()).thenReturn(new Object[]{"lo"});
        assertThat(keys.generateKey("", "original:", point, resolver))
                .isEqualTo("original:5d41402abc4b2a76b9719d911017c592");
    }

    /**
     * 保留四种异常构造方式、请求标识和独立参数副本，业务冲突不采集堆栈。
     */
    @Test
    void shouldPreserveExceptionOverloadsWithoutExposingArray() {
        assertThat(new IdempotentException("message").getRequestId()).isNull();
        assertThat(new IdempotentException("id", "message").getRequestId()).isEqualTo("id");
        assertThat(new IdempotentException("message", 7).getArgs()).containsExactly(7);
        Object[] args = {"original"};
        IdempotentException exception = new IdempotentException("id", "message", args);
        args[0] = "changed";
        exception.getArgs()[0] = "changed";
        assertThat(exception.getArgs()).containsExactly("original");
        assertThat(exception.getStackTrace()).isEmpty();
    }

    /**
     * 旧配置属性可绑定，用户键生成器及切面可覆盖默认 Bean。
     */
    @Test
    void shouldBindPropertiesAndOverrideExtensionPoints() {
        IdempotentKeyGenerator keys = new IdempotentKeyGenerator();
        IdempotentAspect aspect = new IdempotentAspect(executor, keys, new IdempotentProperties());
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(IdempotentConfiguration.class))
                .withPropertyValues("idempotent.debug-log=true", "idempotent.default-expire-seconds=7s")
                .withBean(IdempotentKeyGenerator.class, () -> keys)
                .withBean(IdempotentAspect.class, () -> aspect)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentKeyGenerator.class).hasSingleBean(IdempotentAspect.class);
                    assertThat(context.getBean(IdempotentKeyGenerator.class)).isSameAs(keys);
                    assertThat(context.getBean(IdempotentAspect.class)).isSameAs(aspect);
                    assertThat(context.getBean(IdempotentProperties.class).getDebugLog()).isTrue();
                    assertThat(context.getBean(RedisIdempotentExecutor.class).getDefaultExpireSeconds())
                            .isEqualTo(Duration.ofSeconds(7));
                });
    }

    /**
     * 保留注解默认值和旧配置工厂入口，旧切面方法仍执行受保护业务。
     *
     * @throws Throwable 切面调用失败
     */
    @Test
    void shouldPreserveAnnotationAndOriginalFactories() throws Throwable {
        Idempotent annotation = getClass().getMethod("operation", Object.class).getAnnotation(Idempotent.class);
        assertThat(annotation.expire()).isEqualTo(10);
        assertThat(annotation.keyPrefix()).isEqualTo("idempotent:");
        IdempotentConfiguration configuration = new IdempotentConfiguration();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            RedisIdempotentExecutor original = configuration.redisIdempotentExecutor(client, new IdempotentProperties());
            IdempotentAspect aspect = configuration.idempotentAspect(original, configuration.idempotentKeyGenerator(), context);
            ProceedingJoinPoint point = point("hello");
            when(point.proceed()).thenReturn("success");
            assertThat(aspect.checkIdempotent(point, annotation)).isEqualTo("success");
            verify(completed).set("done", Duration.ofSeconds(10));
        }
    }

    /**
     * 构造带原参数名的连接点，以公开方法验证键协议与切面。
     *
     * @param argument 请求参数
     * @return 调用连接点
     * @throws Exception 样例方法反射失败
     */
    private ProceedingJoinPoint point(Object argument) throws Exception {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = getClass().getMethod("operation", Object.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"request"});
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[]{argument});
        when(point.getTarget()).thenReturn(this);
        return point;
    }

    /**
     * 供反射读取签名和原注解默认值的业务样例。
     *
     * @param request 请求内容
     */
    @Idempotent
    public void operation(Object request) {
    }
}
