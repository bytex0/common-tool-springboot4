package io.github.bytex0.redis;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异地任务执行器(OtherThreadPoolConfig)提供有界 FIFO 队列、TTL 上下文传播和有限关闭等待。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:00:40
 */
@Configuration(proxyBeanMethods = false)
public class OtherThreadPoolConfig {

    /**
     * 保留旧公开 CPU 数常量；复制任务不再据此启动大量工作线程。
     */
    public static final int CPU_NUM = Runtime.getRuntime().availableProcessors();

    /**
     * 保留旧未使用的容量常量；实际容量由 replication-queue-capacity 控制。
     */
    public static final int QUEUE_CAPACITY = 20000;

    /**
     * 保留旧任务线程名前缀。
     */
    public static final String BACK_REDIS_POOL = "back-redis-pool-";

    /**
     * 保留手工创建入口，调用方必须关闭返回的执行器。
     *
     * @return 使用默认容量和超时的执行器
     */
    public ExecutorService otherExecutor() {
        return otherExecutor(new MultiRedisProperties());
    }

    /**
     * 创建容器管理的异地执行器，可按原 Bean 名覆盖。
     *
     * @param properties 有界容量和关闭上限
     * @return 默认单工作线程执行器
     */
    @Bean(name = "otherRoomExecutor", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "otherRoomExecutor")
    public ExecutorService otherExecutor(MultiRedisProperties properties) {
        Assert.isTrue(properties.getReplicationQueueCapacity() > 0, "复制容量必须大于0");
        Assert.notNull(properties.getReplicationTimeout(), "复制超时不能为空");
        Assert.isTrue(properties.getReplicationTimeout().toMillis() > 0, "复制超时至少一毫秒");
        return new ReplicationExecutor(properties.getReplicationQueueCapacity(), properties.getReplicationTimeout());
    }

    /**
     * 有界复制执行器(ReplicationExecutor)在提交时捕获 TTL，并在关闭超时时取消未执行任务。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:00:40
     */
    private static final class ReplicationExecutor extends ThreadPoolExecutor {

        /**
         * 等待执行器结束的有限时长。
         */
        private final Duration timeout;

        /**
         * 创建固定单工作线程池，拒绝策略不使用 CallerRuns 破坏 FIFO。
         *
         * @param capacity 等待队列容量
         * @param timeout 停止等待上限
         */
        private ReplicationExecutor(int capacity, Duration timeout) {
            super(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity),
                    Thread.ofPlatform().name(BACK_REDIS_POOL, 0).factory(), new AbortPolicy());
            this.timeout = timeout;
        }

        /**
         * 提交时捕获 TTL，上下文在任务结束后恢复。
         *
         * @param command 待执行任务
         */
        @Override
        public void execute(Runnable command) {
            super.execute(TtlRunnable.get(command, false, true));
        }

        /**
         * 限时排空，超时后取消等待任务；不会像默认 ExecutorService.close 无限等待。
         */
        @Override
        public void close() {
            shutdown();
            try {
                if (!awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    cancelPending();
                }
            } catch (InterruptedException exception) {
                cancelPending();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("停止Redis复制执行器被中断", exception);
            }
        }

        /**
         * 中断工作线程并释放队列中尚未执行的任务引用。
         */
        private void cancelPending() {
            List<Runnable> discarded = shutdownNow();
            for (Runnable task : discarded) {
                Runnable original = task instanceof TtlRunnable ttl ? ttl.getRunnable() : task;
                if (original instanceof Future<?> future) {
                    future.cancel(false);
                }
            }
        }
    }
}
