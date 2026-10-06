package io.github.bytex0.excel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Excel 配置(ExcelProperties)提供能力开关及实际生效的有界线程池选项。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:15:35
 */
@Data
@ConfigurationProperties("excel")
public class ExcelProperties {

    /**
     * 是否启用 Starter，默认 true；false 不注册模板和默认执行器。
     */
    private boolean enabled = true;

    /**
     * 专用执行器配置，沿用原文档 excel.thread-pool 前缀。
     */
    private ThreadPool threadPool = new ThreadPool();

    /**
     * 线程池选项(ThreadPool)限制并发、积压和关闭等待，不使用无界 per-task 执行器。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:15:35
     */
    @Data
    public static class ThreadPool {

        /**
         * 核心线程数，默认 4，必须大于 0 且不超过 maxSize。
         */
        private int coreSize = 4;

        /**
         * 最大线程数，默认 4，必须大于等于核心线程数。
         */
        private int maxSize = 4;

        /**
         * 等待任务容量，默认 128，必须大于 0；满载时明确拒绝，不在调用线程执行。
         */
        private int queueCapacity = 128;

        /**
         * 非核心线程空闲回收秒数，默认 60，不能为负数。
         */
        private long keepAliveSeconds = 60;

        /**
         * 关闭时等待排空的上限，默认 10 秒，必须为正；超时后中断任务，业务须响应中断。
         */
        private Duration shutdownTimeout = Duration.ofSeconds(10);
    }
}
