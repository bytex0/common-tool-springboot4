package io.github.bytex0.excel.core.importer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Duration;

/**
 * 并发导入上下文(LargeDataImportContext)配置有界消费、批次事务和一致的进度回调。
 *
 * @param <R> 行类型
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LargeDataImportContext<R> extends ImportContext<R> {

    /**
     * 可选业务任务标识，由业务回调使用，不用作共享静态状态键。
     */
    private String uniqueId;

    /**
     * 并发导入批次大小，默认 1000，范围 1 至 10000；覆盖继承的 batchCount。
     */
    private Integer batchSize = 1000;

    /**
     * 允许排队的批次数，默认 10，范围 1 至 1024；不包含正在处理的批次。
     */
    private Integer queueSize = 10;

    /**
     * 本次导入最多并发处理的批次数，默认 4，范围 1 至 128；实际还受执行器容量限制。
     */
    private Integer threadCount = 4;

    /**
     * 是否为每批开启独立事务，默认 true；必须向导入器提供 PlatformTransactionManager。
     * 原字段未生效，现在缺少事务管理器时明确失败，不假装支持事务。
     */
    private Boolean enableTransaction = true;

    /**
     * 业务批次失败后是否继续，默认 false；解析、调度和进度回调错误始终终止。
     */
    private Boolean continueOnError = false;

    /**
     * 可选进度回调，由调用线程串行通知，不在工作线程持锁回调。
     * current 始终等于 success+failed；读取未完成时 total=-1，最终通知包含已读取总行数。
     */
    private ImportProgressCallback progressCallback;

    /**
     * 分批读取、排队及处理等待的协作式预算，默认 5 分钟，必须为正。
     * 不强制中断调用方输入 I/O 或进度回调；业务回调须响应中断并设置自身 I/O 超时。
     */
    private Duration operationTimeout = Duration.ofMinutes(5);

    /**
     * 取消后等待实际工作任务退出的上限，默认 10 秒，必须为正。
     */
    private Duration cancellationTimeout = Duration.ofSeconds(10);

    /**
     * 导入进度(ImportProgressCallback)保留原四个计数参数，不传递敏感行内容。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:57:29
     */
    @FunctionalInterface
    public interface ImportProgressCallback {

        /**
         * 通知一致的批次计数快照；不提供每行事务提交结果的推测。
         *
         * @param current 已处理数量，包含成功和失败批次
         * @param total 已读取总行数，读取未完成时为 -1
         * @param success 成功批次的行数
         * @param failed 失败批次的行数
         */
        void onProgress(long current, long total, long success, long failed);
    }
}
