package io.github.bytex0.excel.core.importer;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.ExcelSupport;
import io.github.bytex0.excel.core.TrackedExcelTask;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 并发 Excel 导入器(LargeDataExcelImporter)保留原业务回调，使用有限在途任务和真实批次事务。
 * 主调用等待完成；业务回调在共享执行器运行，进度由调用线程串行通知。
 * 停止不会回滚先前已提交的批次，业务回调必须响应中断并管理自己的 I/O 超时。
 *
 * @param <R> 行类型
 * @author linshiqiang
 * @since 2026-10-06 11:06:59
 */
public abstract class LargeDataExcelImporter<R> {

    /**
     * 单次导入的并发任务上限。
     */
    private static final int MAX_WORKERS = 128;

    /**
     * 单次导入的排队批次数上限。
     */
    private static final int MAX_QUEUE_SIZE = 1024;

    /**
     * 调用方或容器拥有的共享执行器。
     */
    protected final ExecutorService executorService;

    /**
     * 可选事务管理器；开启事务时必须提供。
     */
    private final PlatformTransactionManager transactionManager;

    /**
     * 保留原构造入口；此入口的上下文必须明确关闭事务。
     *
     * @param executorService 共享执行器
     */
    public LargeDataExcelImporter(@Qualifier("excelThreadPool") ExecutorService executorService) {
        this(executorService, null);
    }

    /**
     * 创建支持每批独立事务的导入器，不关闭外部执行器和事务管理器。
     *
     * @param executorService 共享执行器
     * @param transactionManager 事务管理器，不启用事务时允许 null
     */
    public LargeDataExcelImporter(ExecutorService executorService, PlatformTransactionManager transactionManager) {
        Assert.notNull(executorService, "Excel执行器不能为空");
        this.executorService = executorService;
        this.transactionManager = transactionManager;
    }

    /**
     * 处理本批数据；批次容器属于本次调用，可在回调内修改，不影响读入缓冲或计数。
     *
     * @param dataList 批次数据
     * @param context 本次导入上下文，调用期间不得修改配置
     */
    public abstract void handleImportData(List<R> dataList, LargeDataImportContext<R> context);

    /**
     * 保留原阻塞等待入口；内部并发消费，失败会取消并有限等待实际工作退出。
     *
     * @param context 导入参数
     * @throws Exception 文件打开、解析、业务或取消失败时抛出
     */
    public void importLargeExcel(LargeDataImportContext<R> context) throws Exception {
        importLargeExcelWithResult(context);
    }

    /**
     * 导入并返回一致的成功/失败批次行数，成功返回意味着本次任务全部结束。
     *
     * @param context 导入参数
     * @return 已处理行数
     * @throws Exception 文件或导入失败时抛出
     */
    public ExcelTemplate.ImportResult importLargeExcelWithResult(LargeDataImportContext<R> context) throws Exception {
        int sheet = ExcelSupport.validateImport(context);
        validate(context);
        try (InputStream input = context.getFile().getInputStream(); ImportSession session = new ImportSession(context)) {
            new ExcelTemplate().readWithListener(input, context.getEntityClass(), sheet, session);
            return session.finish();
        }
    }

    /**
     * 在打开文件或发出任务前验证容量、时间和事务依赖。
     *
     * @param context 上下文
     */
    private void validate(LargeDataImportContext<R> context) {
        Assert.notNull(context.getBatchSize(), "批次大小不能为空");
        Assert.notNull(context.getQueueSize(), "队列容量不能为空");
        Assert.notNull(context.getThreadCount(), "线程数不能为空");
        Assert.notNull(context.getEnableTransaction(), "事务开关不能为空");
        Assert.notNull(context.getContinueOnError(), "继续模式不能为空");
        Assert.isTrue(context.getBatchSize() > 0 && context.getBatchSize() <= ExcelTemplate.MAX_BATCH_ROWS,
                "批次大小必须在1至10000之间");
        Assert.isTrue(context.getQueueSize() > 0 && context.getQueueSize() <= MAX_QUEUE_SIZE,
                "队列批次容量越界");
        Assert.isTrue(context.getThreadCount() > 0 && context.getThreadCount() <= MAX_WORKERS, "并发线程数越界");
        Assert.isTrue(!context.getEnableTransaction() || transactionManager != null, "开启事务必须提供事务管理器");
        positive(context.getOperationTimeout());
        positive(context.getCancellationTimeout());
    }

    /**
     * 检查正且可换算为纳秒的时间值。
     *
     * @param duration 时间值
     */
    private static void positive(Duration duration) {
        Assert.notNull(duration, "超时时间不能为空");
        Assert.isTrue(!duration.isNegative() && !duration.isZero(), "超时时间必须为正");
        duration.toNanos();
    }

    /**
     * 批次结果(BatchOutcome)只记录原始批次大小与错误，不共享业务行内容。
     *
     * @param size 原始批次大小
     * @param failure 业务异常，null 表示成功提交
     * @author linshiqiang
     * @since 2026-10-06 11:06:59
     */
    private record BatchOutcome(
            /**
             * 提交给业务处理前的批次行数。
             */
            int size,

            /**
             * 业务或事务异常，成功时为 null。
             */
            Exception failure) {
    }

    /**
     * 单次导入作用域(ImportSession)拥有缓冲和任务，不使用静态可变计数器。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:06:59
     */
    private final class ImportSession implements ReadListener<R>, AutoCloseable {

        /**
         * 本次业务上下文。
         */
        private final LargeDataImportContext<R> context;

        /**
         * 读取端单批大小。
         */
        private final int batchSize;

        /**
         * 运行和排队任务总上限。
         */
        private final int window;

        /**
         * 实际并发处理许可，不持互斥锁调用业务。
         */
        private final Semaphore workers;

        /**
         * 有界完成队列；最大节点数不会超过在途窗口。
         */
        private final ArrayBlockingQueue<TrackedExcelTask<BatchOutcome>> completed;

        /**
         * 尚未由读取线程归集结果的任务，只在读取线程修改。
         */
        private final Set<TrackedExcelTask<BatchOutcome>> active = new LinkedHashSet<>();

        /**
         * 读取端当前批次。
         */
        private final List<R> buffered;

        /**
         * 可选的独立批次事务模板。
         */
        private final TransactionTemplate transactions;

        /**
         * 本次进度回调，读取开始时固定引用。
         */
        private final LargeDataImportContext.ImportProgressCallback progress;

        /**
         * 是否继续业务批次错误。
         */
        private final boolean continueOnError;

        /**
         * 协作式操作截止时间。
         */
        private final long deadline;

        /**
         * 操作开始时固定的取消预算，业务回调修改上下文不会扩大清理等待。
         */
        private final Duration cancellationTimeout;

        /**
         * 是否已完整读取文件。
         */
        private boolean readingComplete;

        /**
         * 已解析的行数。
         */
        private long read;

        /**
         * 已归集成功批次行数。
         */
        private long success;

        /**
         * 已归集失败批次行数。
         */
        private long failed;

        /**
         * 创建作用域，暂不提交工作任务。
         *
         * @param context 已校验的导入配置
         */
        private ImportSession(LargeDataImportContext<R> context) {
            this.context = context;
            this.batchSize = context.getBatchSize();
            this.window = context.getThreadCount() + context.getQueueSize();
            this.workers = new Semaphore(context.getThreadCount());
            this.completed = new ArrayBlockingQueue<>(window);
            this.buffered = new ArrayList<>(batchSize);
            this.progress = context.getProgressCallback();
            this.continueOnError = context.getContinueOnError();
            this.deadline = System.nanoTime() + context.getOperationTimeout().toNanos();
            this.cancellationTimeout = context.getCancellationTimeout();
            if (context.getEnableTransaction()) {
                this.transactions = new TransactionTemplate(transactionManager);
                this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                this.transactions.setTimeout((int) Math.min(Integer.MAX_VALUE,
                        Math.max(1, context.getOperationTimeout().toSeconds())));
            } else {
                this.transactions = null;
            }
        }

        /**
         * 接收解析行，定期检查失败并保持缓冲有界。
         *
         * @param data 当前行
         * @param analysis 解析上下文
         */
        @Override
        public void invoke(R data, AnalysisContext analysis) {
            checkDeadline();
            while (consume(false)) {
                // 尽早归集已完成任务，避免失败后仍继续读取大量行。
            }
            read++;
            buffered.add(data);
            if (buffered.size() == batchSize) {
                flush();
            }
        }

        /**
         * 提交最后一批并标记已知总数，不在解析器回调内等待所有业务任务。
         *
         * @param analysis 解析上下文
         */
        @Override
        public void doAfterAllAnalysed(AnalysisContext analysis) {
            flush();
            readingComplete = true;
        }

        /**
         * 等待剩余结果并发送最终一致的计数。
         *
         * @return 导入结果
         */
        private ExcelTemplate.ImportResult finish() {
            Assert.state(readingComplete, "Excel读取未完整结束");
            while (!active.isEmpty()) {
                consume(true);
            }
            Assert.state(success + failed == read, "Excel批次统计与读取行数不一致");
            report();
            return new ExcelTemplate.ImportResult(success, failed);
        }

        /**
         * 提交一个独立可修改的批次，在满窗口时先归集完成结果，不无限 put 等待消费者。
         */
        private void flush() {
            if (buffered.isEmpty()) {
                return;
            }
            checkDeadline();
            while (active.size() >= window) {
                consume(true);
            }
            List<R> batch = new ArrayList<>(buffered);
            buffered.clear();
            int size = batch.size();
            TrackedExcelTask<BatchOutcome> task = new TrackedExcelTask<>(() -> process(batch, size), completed::add);
            active.add(task);
            try {
                executorService.execute(task);
            } catch (RuntimeException exception) {
                active.remove(task);
                task.cancel(true);
                throw exception;
            }
        }

        /**
         * 在工作线程执行一批，事务开启时失败自动回滚该批。
         *
         * @param batch 独立批次
         * @param size 原始批次数量
         * @return 成功或业务错误结果
         * @throws InterruptedException 获取并发许可被中断时抛出
         * @throws TimeoutException 获取并发许可超时时抛出
         */
        private BatchOutcome process(List<R> batch, int size) throws InterruptedException, TimeoutException {
            if (!workers.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException("Excel并发许可等待超时");
            }
            try {
                try {
                    if (transactions == null) {
                        handleImportData(batch, context);
                    } else {
                        transactions.executeWithoutResult(status -> handleImportData(batch, context));
                    }
                    return new BatchOutcome(size, null);
                } catch (Exception exception) {
                    if (exception instanceof InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        InterruptedException interrupted = new InterruptedException("Excel业务线程已中断");
                        interrupted.initCause(exception);
                        throw interrupted;
                    }
                    return new BatchOutcome(size, exception);
                }
            } finally {
                workers.release();
            }
        }

        /**
         * 在调用线程归集一个完成结果，保持 current=success+failed。
         *
         * @param wait 是否需要等到至少一个结果
         * @return 是否消费到一个结果
         */
        private boolean consume(boolean wait) {
            try {
                TrackedExcelTask<BatchOutcome> task = wait
                        ? completed.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
                        : completed.poll();
                if (task == null) {
                    Assert.state(!wait, "等待Excel批次处理超时");
                    return false;
                }
                active.remove(task);
                BatchOutcome outcome = task.get();
                if (outcome.failure() == null) {
                    success += outcome.size();
                } else {
                    failed += outcome.size();
                }
                report();
                if (outcome.failure() != null && !continueOnError) {
                    throw new IllegalStateException("Excel导入业务批次失败", outcome.failure());
                }
                return true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待Excel导入被中断", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Excel导入工作线程失败", exception.getCause());
            }
        }

        /**
         * 在读取和提交检查点检查预算以及调用线程中断。
         */
        private void checkDeadline() {
            Assert.state(!Thread.currentThread().isInterrupted(), "Excel导入调用线程已中断");
            Assert.state(deadline - System.nanoTime() > 0, "Excel导入时间预算已耗尽");
        }

        /**
         * 调用进度回调，不持有锁，回调错误直接终止操作。
         */
        private void report() {
            if (progress != null) {
                progress.onProgress(success + failed, readingComplete ? read : -1, success, failed);
            }
        }

        /**
         * 取消全部在途任务，并在共同预算内等待真正退出；超时明确失败而不假装已停止。
         */
        @Override
        public void close() {
            List<TrackedExcelTask<BatchOutcome>> tasks = List.copyOf(active);
            tasks.forEach(task -> task.cancel(true));
            long cancellationDeadline = System.nanoTime() + cancellationTimeout.toNanos();
            boolean terminated = true;
            for (TrackedExcelTask<BatchOutcome> task : tasks) {
                Duration remaining = Duration.ofNanos(Math.max(0, cancellationDeadline - System.nanoTime()));
                terminated &= task.cancelAndAwait(remaining);
            }
            active.clear();
            completed.clear();
            buffered.clear();
            Assert.state(terminated, "Excel导入任务取消后未及时退出");
        }
    }
}
