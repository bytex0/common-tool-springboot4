package io.github.bytex0.excel;

import io.github.bytex0.excel.core.importer.LargeDataExcelImporter;
import io.github.bytex0.excel.core.importer.LargeDataImportContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 并发导入测试(LargeImportTest)验证失败不会阻塞读入，并检查回调计数的单线程一致性。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:06:59
 */
class LargeImportTest {

    /**
     * 继续模式中失败批次也计入 current，业务清空其批次列表不改变原始行数。
     *
     * @throws Exception 文件或导入失败时抛出
     */
    @Test
    void shouldCountFailuresAndReportConsistentProgress() throws Exception {
        try (ExecutorService executor = executor()) {
            LargeDataImportContext<ExcelTemplateTest.Row> context = context(5);
            context.setContinueOnError(true);
            List<List<Long>> progress = new ArrayList<>();
            Thread caller = Thread.currentThread();
            context.setProgressCallback((current, total, success, failed) -> {
                assertThat(Thread.currentThread()).isSameAs(caller);
                assertThat(current).isEqualTo(success + failed);
                progress.add(List.of(current, total, success, failed));
            });
            ExcelTemplate.ImportResult result = new Importer(executor).importLargeExcelWithResult(context);
            assertThat(result.success()).isEqualTo(3);
            assertThat(result.failed()).isEqualTo(2);
            assertThat(progress.getLast()).containsExactly(5L, 5L, 3L, 2L);
        }
    }

    /**
     * 一个消费任务失败后必须终止而非让生产者永久等待满队列。
     */
    @Test
    void shouldStopOnFailureAndRequireRealTransactionSupport() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (ExecutorService executor = executor()) {
                LargeDataImportContext<ExcelTemplateTest.Row> context = context(100);
                assertThatThrownBy(() -> new Importer(executor).importLargeExcel(context))
                        .isInstanceOf(Exception.class).hasRootCauseMessage("expected batch failure");
                context.setEnableTransaction(true);
                assertThatThrownBy(() -> new Importer(executor).importLargeExcel(context))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("事务");
            }
        });
    }

    /**
     * 失败任务触发取消后，另一个已运行回调必须实际退出，不能只让 Future 标记完成。
     *
     * @throws Exception 等待工作线程信号失败时抛出
     */
    @Test
    void shouldInterruptAndWaitForRunningBusinessCallback() throws Exception {
        try (ExecutorService executor = executor()) {
            CancellationImporter importer = new CancellationImporter(executor);
            assertThatThrownBy(() -> importer.importLargeExcel(context(4))).isInstanceOf(Exception.class);
            assertThat(importer.exited.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * 创建有界的测试执行器。
     *
     * @return 需要关闭的执行器
     */
    private ExecutorService executor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 生成隔离工作簿，默认不使用事务。
     *
     * @param count 行数
     * @return 测试上下文
     */
    private LargeDataImportContext<ExcelTemplateTest.Row> context(int count) {
        List<ExcelTemplateTest.Row> rows = IntStream.rangeClosed(1, count).mapToObj(index -> {
            ExcelTemplateTest.Row row = new ExcelTemplateTest.Row();
            row.setId(index);
            return row;
        }).toList();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicBoolean sent = new AtomicBoolean();
        new ExcelTemplate().write(bytes, ExcelTemplateTest.Row.class, "Input",
                () -> sent.getAndSet(true) ? List.of() : rows, 1000);
        LargeDataImportContext<ExcelTemplateTest.Row> context = new LargeDataImportContext<>();
        context.setFile(new MockMultipartFile("file", "data.xlsx", null, bytes.toByteArray()));
        context.setEntityClass(ExcelTemplateTest.Row.class);
        context.setBatchSize(2);
        context.setQueueSize(1);
        context.setThreadCount(2);
        context.setEnableTransaction(false);
        context.setOperationTimeout(Duration.ofSeconds(2));
        context.setCancellationTimeout(Duration.ofSeconds(1));
        return context;
    }

    /**
     * 业务导入器(Importer)模拟一批数据处理失败。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:06:59
     */
    private static class Importer extends LargeDataExcelImporter<ExcelTemplateTest.Row> {

        /**
         * 使用共享执行器。
         *
         * @param executor 执行器
         */
        Importer(ExecutorService executor) {
            super(executor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleImportData(List<ExcelTemplateTest.Row> rows,
                                     LargeDataImportContext<ExcelTemplateTest.Row> context) {
            if (rows.stream().anyMatch(row -> row.getId() == 3)) {
                throw new IllegalStateException("expected batch failure");
            }
            rows.clear();
        }
    }

    /**
     * 取消测试导入器(CancellationImporter)一批等待中断，另一批主动失败。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    private static class CancellationImporter extends LargeDataExcelImporter<ExcelTemplateTest.Row> {

        /**
         * 等待批次已开始。
         */
        private final CountDownLatch started = new CountDownLatch(1);

        /**
         * 等待批次已经离开业务回调。
         */
        private final CountDownLatch exited = new CountDownLatch(1);

        /**
         * 只有中断才提前结束的等待信号。
         */
        private final CountDownLatch blocker = new CountDownLatch(1);

        /**
         * 注入执行器。
         *
         * @param executor 执行器
         */
        CancellationImporter(ExecutorService executor) {
            super(executor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleImportData(List<ExcelTemplateTest.Row> rows,
                                     LargeDataImportContext<ExcelTemplateTest.Row> context) {
            if (rows.getFirst().getId() == 1) {
                started.countDown();
                try {
                    if (!blocker.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("cancel did not interrupt worker");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("expected cancellation", exception);
                } finally {
                    exited.countDown();
                }
            } else {
                try {
                    assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("unexpected interruption", exception);
                }
                throw new IllegalStateException("expected partner failure");
            }
        }
    }
}
