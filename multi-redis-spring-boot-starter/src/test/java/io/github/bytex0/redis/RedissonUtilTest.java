package io.github.bytex0.redis;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import com.alibaba.ttl.TransmittableThreadLocal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 工具测试(RedissonUtilTest)通过公开入口验证同步失败和异步双写。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:58:09
 */
class RedissonUtilTest {

    /**
     * 主库失败必须向调用者传播，不能再返回伪成功或 null。
     */
    @Test
    void shouldPropagatePrimaryFailure() {
        RedissonClient primary = mock(RedissonClient.class);
        RBucket<Object> bucket = mock(RBucket.class);
        when(primary.getBucket("key")).thenReturn(bucket);
        doThrow(new IllegalStateException("offline")).when(bucket).set("value");
        try (RedissonUtil util = new RedissonUtil(primary, JsonMapper.builder().build())) {
            assertThatThrownBy(() -> util.set("key", "value")).isInstanceOf(IllegalStateException.class);
        }
    }

    /**
     * 显式备库使用异步双写，并可通过等待入口确认结果。
     */
    @Test
    void shouldMirrorWritesAndExposeBackupFailure() {
        RedissonClient primary = mock(RedissonClient.class);
        RedissonClient backup = mock(RedissonClient.class);
        RBucket<Object> primaryBucket = mock(RBucket.class);
        RBucket<Object> backupBucket = mock(RBucket.class);
        when(primary.getBucket("key")).thenReturn(primaryBucket);
        when(backup.getBucket("key")).thenReturn(backupBucket);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2), new ThreadPoolExecutor.AbortPolicy());
        try (executor; RedissonUtil util = new RedissonUtil(primary, backup, JsonMapper.builder().build(),
                executor, 2, Duration.ofSeconds(2))) {
            assertThat(util.set("key", "value")).isTrue();
            util.awaitReplication(Duration.ofSeconds(2));
            verify(backupBucket).set("value");
            doThrow(new IllegalStateException("backup offline")).when(backupBucket).set("failure");
            util.set("key", "failure");
            assertThatThrownBy(() -> util.awaitReplication(Duration.ofSeconds(2)))
                    .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(util.replicationFailures()).isEqualTo(1);
        }
    }

    /**
     * 有界复制必须在主写前拒绝超额任务，并按原调用顺序传播每次提交的 TTL 上下文。
     *
     * @throws Exception 测试等待或线程执行失败时抛出
     */
    @Test
    void shouldPreserveOrderContextAndRejectBeforePrimaryWrite() throws Exception {
        RedissonClient primary = mock(RedissonClient.class);
        RedissonClient backup = mock(RedissonClient.class);
        RBucket<Object> primaryBucket = mock(RBucket.class);
        RBucket<Object> backupBucket = mock(RBucket.class);
        when(primary.getBucket("key")).thenReturn(primaryBucket);
        when(backup.getBucket("key")).thenReturn(backupBucket);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> observed = new ConcurrentLinkedQueue<>();
        TransmittableThreadLocal<String> context = new TransmittableThreadLocal<>();
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            observed.add(invocation.getArgument(0) + ":" + context.get());
            return null;
        }).when(backupBucket).set(any());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2), new ThreadPoolExecutor.AbortPolicy());
        try (executor; RedissonUtil util = new RedissonUtil(primary, backup, JsonMapper.builder().build(),
                executor, 2, Duration.ofSeconds(2))) {
            try {
                context.set("one");
                util.set("key", "first");
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                context.set("two");
                util.set("key", "second");
                assertThatThrownBy(() -> util.set("key", "rejected"))
                        .isInstanceOf(RejectedExecutionException.class);
                verify(primaryBucket, never()).set("rejected");
            } finally {
                release.countDown();
                context.remove();
            }
            util.awaitReplication(Duration.ofSeconds(2));
            assertThat(observed).containsExactly("first:one", "second:two");
        }
    }

    /**
     * 关闭后的写入不再执行，队列等待中断必须保留线程标志和异常原因。
     *
     * @throws Exception 替身方法声明的中断异常
     */
    @Test
    void shouldPreserveInterruptionAndRejectClosedWrites() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RBlockingQueue<Object> queue = mock(RBlockingQueue.class);
        when(client.getBlockingQueue("queue")).thenReturn(queue);
        when(queue.take()).thenThrow(new InterruptedException("cancelled"));
        RedissonUtil util = new RedissonUtil(client, JsonMapper.builder().build());
        try {
            assertThatThrownBy(() -> util.takeBlockingQueue("queue"))
                    .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            util.close();
        }
        assertThatThrownBy(() -> util.set("key", "value")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> util.setNx("key", "value", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        verify(client, never()).getBucket("key");
    }

    /**
     * 非法参数在发起 Redis 命令前失败，空集合读取和删除具有确定行为。
     */
    @Test
    void shouldValidateWithoutNetworkSideEffects() {
        RedissonClient client = mock(RedissonClient.class);
        try (RedissonUtil util = new RedissonUtil(client, JsonMapper.builder().build())) {
            assertThatThrownBy(() -> util.set("key", "value", Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> util.zadd("key", "value", Double.NaN))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> util.spop("key", -1)).isInstanceOf(IllegalArgumentException.class);
            assertThat(util.pfcountUnion(List.of())).isZero();
            util.delete(List.of());
            verify(client, never()).getBucket(anyString());
        }
    }
}
