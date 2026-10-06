package io.github.bytex0.excel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Excel 执行器配置(ExcelThreadPoolConfig)保留原 Bean 名，使用有界队列与有限关闭等待。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:15:35
 */
@Configuration(proxyBeanMethods = false)
public class ExcelThreadPoolConfig {

    /**
     * 保留原手工工厂入口，返回值必须由调用方关闭。
     *
     * @return 默认有界执行器
     */
    public ExecutorService excelThreadPool() {
        return excelThreadPool(new ExcelProperties());
    }

    /**
     * 创建容器管理的执行器，用户按原名称提供 Bean 时退让。
     *
     * @param properties 已绑定配置
     * @return 有界执行器
     */
    @Bean(name = "excelThreadPool", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "excelThreadPool")
    public ExecutorService excelThreadPool(ExcelProperties properties) {
        ExcelProperties.ThreadPool options = properties.getThreadPool();
        Assert.notNull(options, "Excel线程池配置不能为空");
        Assert.isTrue(options.getCoreSize() > 0 && options.getMaxSize() >= options.getCoreSize(),
                "Excel线程数配置不合法");
        Assert.isTrue(options.getQueueCapacity() > 0 && options.getKeepAliveSeconds() >= 0,
                "Excel队列和空闲时间配置不合法");
        Assert.notNull(options.getShutdownTimeout(), "Excel关闭等待不能为空");
        Assert.isTrue(options.getShutdownTimeout().toNanos() > 0, "Excel关闭等待必须为正");
        return new ManagedExecutor(options);
    }

    /**
     * 托管执行器(ManagedExecutor)关闭超时后取消排队 Future，避免其永久等待执行。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:15:35
     */
    private static final class ManagedExecutor extends ThreadPoolExecutor {

        /**
         * 关闭排空上限。
         */
        private final Duration shutdownTimeout;

        /**
         * 创建固定容量的线程池。
         *
         * @param options 已校验选项
         */
        private ManagedExecutor(ExcelProperties.ThreadPool options) {
            super(options.getCoreSize(), options.getMaxSize(), options.getKeepAliveSeconds(), TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(options.getQueueCapacity()),
                    Thread.ofPlatform().name("excel-pool-", 0).factory(), new AbortPolicy());
            this.shutdownTimeout = options.getShutdownTimeout();
        }

        /**
         * 有限等待正常完成，超时或中断时请求取消；不使用 ExecutorService 默认的无限 close。
         */
        @Override
        public void close() {
            shutdown();
            try {
                if (!awaitTermination(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    cancelQueued();
                }
            } catch (InterruptedException exception) {
                cancelQueued();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("停止Excel线程池被中断", exception);
            }
        }

        /**
         * 中断运行任务并完成排队 Future 的取消状态。
         */
        private void cancelQueued() {
            for (Runnable task : shutdownNow()) {
                if (task instanceof Future<?> future) {
                    future.cancel(false);
                }
            }
        }
    }
}
