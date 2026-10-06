package io.github.bytex0.excel;

import io.github.bytex0.excel.core.importer.LargeDataExcelImporter;
import io.github.bytex0.excel.core.importer.LargeDataImportContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel 事务测试(ExcelTransactionTest)使用独立 H2 验证批次回滚，不能只检查是否调用事务模板。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:15:35
 */
class ExcelTransactionTest {

    /**
     * 开启事务后失败批次完全回滚，关闭事务则明确保留业务此前已写入的行。
     *
     * @throws Exception 导入失败时抛出
     */
    @Test
    void shouldRollbackOnlyFailedBatchWhenTransactionsEnabled() throws Exception {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try (ExecutorService executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy())) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE imported_row (id INTEGER PRIMARY KEY)");
            DatabaseImporter importer = new DatabaseImporter(executor, new DataSourceTransactionManager(database), jdbc);
            LargeDataImportContext<ExcelTemplateTest.Row> context = context();
            var result = importer.importLargeExcelWithResult(context);
            assertThat(result.success()).isEqualTo(3);
            assertThat(result.failed()).isEqualTo(2);
            assertThat(jdbc.queryForList("SELECT id FROM imported_row ORDER BY id", Integer.class))
                    .containsExactly(1, 2, 5);
            jdbc.update("DELETE FROM imported_row");
            context.setEnableTransaction(false);
            importer.importLargeExcel(context);
            assertThat(jdbc.queryForList("SELECT id FROM imported_row ORDER BY id", Integer.class))
                    .containsExactly(1, 2, 3, 5);
        } finally {
            database.shutdown();
        }
    }

    /**
     * 创建五行输入，第二批包含故意失败的编号 3。
     *
     * @return 默认启用事务、继续错误的上下文
     */
    private LargeDataImportContext<ExcelTemplateTest.Row> context() {
        List<ExcelTemplateTest.Row> rows = IntStream.rangeClosed(1, 5).mapToObj(index -> {
            ExcelTemplateTest.Row row = new ExcelTemplateTest.Row();
            row.setId(index);
            return row;
        }).toList();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicBoolean sent = new AtomicBoolean();
        new ExcelTemplate().write(bytes, ExcelTemplateTest.Row.class, "Input",
                () -> sent.getAndSet(true) ? List.of() : rows, 100);
        LargeDataImportContext<ExcelTemplateTest.Row> context = new LargeDataImportContext<>();
        context.setFile(new MockMultipartFile("file", "input.xlsx", null, bytes.toByteArray()));
        context.setEntityClass(ExcelTemplateTest.Row.class);
        context.setBatchSize(2);
        context.setThreadCount(2);
        context.setQueueSize(1);
        context.setContinueOnError(true);
        return context;
    }

    /**
     * 数据库处理器(DatabaseImporter)让 Starter 控制每批事务，业务只实现写入回调。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:15:35
     */
    private static class DatabaseImporter extends LargeDataExcelImporter<ExcelTemplateTest.Row> {

        /**
         * 本测试独有的数据库访问器。
         */
        private final JdbcTemplate jdbc;

        /**
         * 提供真实事务管理器。
         *
         * @param executor 执行器
         * @param transactions 事务管理器
         * @param jdbc 数据库访问器
         */
        DatabaseImporter(ExecutorService executor, DataSourceTransactionManager transactions, JdbcTemplate jdbc) {
            super(executor, transactions);
            this.jdbc = jdbc;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleImportData(List<ExcelTemplateTest.Row> rows,
                                     LargeDataImportContext<ExcelTemplateTest.Row> context) {
            for (ExcelTemplateTest.Row row : rows) {
                jdbc.update("INSERT INTO imported_row (id) VALUES (?)", row.getId());
                if (row.getId() == 3) {
                    throw new IllegalStateException("expected transactional failure");
                }
            }
        }
    }
}
