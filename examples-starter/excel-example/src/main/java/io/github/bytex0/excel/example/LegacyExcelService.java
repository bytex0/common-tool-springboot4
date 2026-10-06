package io.github.bytex0.excel.example;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.AbstractSimpleExcelProcessor;
import io.github.bytex0.excel.core.exporter.ExportContext;
import io.github.bytex0.excel.core.exporter.LargeExcelZipExportContext;
import io.github.bytex0.excel.core.exporter.LargeExcelZipExporter;
import io.github.bytex0.excel.core.exporter.MultiSheetExcelExporter;
import io.github.bytex0.excel.core.exporter.MultiSheetExportContext;
import io.github.bytex0.excel.core.importer.ImportContext;
import io.github.bytex0.excel.core.importer.LargeDataExcelImporter;
import io.github.bytex0.excel.core.importer.LargeDataImportContext;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

/**
 * Excel 原接口联调(LegacyExcelService)仅实现业务数据回调，调度、事务和读写由 Starter 提供。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:23:54
 */
@Service
public class LegacyExcelService {

    /**
     * Starter 自动装配的有界执行器。
     */
    private final ExecutorService executor;

    /**
     * 示例独有的数据库。
     */
    private final JdbcTemplate jdbc;

    /**
     * 交给 Starter 执行批次事务的管理器。
     */
    private final PlatformTransactionManager transactions;

    /**
     * 本示例独有的随机临时目录。
     */
    private final Path directory;

    /**
     * 注入真实 Starter 依赖并创建隔离临时根目录。
     *
     * @param executor 自动装配的执行器
     * @param jdbc 独立数据库
     * @param transactions 事务管理器
     * @throws IOException 临时目录创建失败时抛出
     */
    public LegacyExcelService(@Qualifier("excelThreadPool") ExecutorService executor, JdbcTemplate jdbc,
                               PlatformTransactionManager transactions) throws IOException {
        this.executor = executor;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.directory = Files.createTempDirectory("excel-example-");
    }

    /**
     * 通过原三个导出处理器生成文件，传入分页条件而不绕过 Starter。
     *
     * @param mode simple、multi 或 zip
     * @param count 合成行数，范围 0 至 10000
     * @param pageSize 分页大小
     * @param rows 每 Sheet 或文件行数
     * @param response HTTP 响应
     * @throws Exception 导出失败时抛出
     */
    public void export(String mode, int count, int pageSize, int rows, HttpServletResponse response) throws Exception {
        Assert.isTrue(count >= 0 && count <= 10000, "合成行数越界");
        Assert.isTrue(pageSize > 0 && pageSize <= 10000 && rows > 0 && rows <= 1000000, "分页或Sheet上限越界");
        switch (mode) {
            case "simple" -> {
                ExportContext<ExcelRow, Integer> context = new ExportContext<>();
                fill(context, count, pageSize);
                new SimpleProcessor(executor, jdbc, null).exportExcel(context, response);
            }
            case "multi" -> {
                MultiSheetExportContext<ExcelRow, Integer> context = new MultiSheetExportContext<>();
                fill(context, count, pageSize);
                context.setMaxRowsPerSheet(rows);
                new SheetExporter(executor).exportMultiSheetExcel(context, response);
            }
            case "zip" -> {
                LargeExcelZipExportContext<ExcelRow, Integer> context = new LargeExcelZipExportContext<>();
                fill(context, count, pageSize);
                context.setFetchSize(pageSize);
                context.setMaxRowsPerSheet(rows);
                context.setTempDir(directory.toString());
                new ZipExporter(executor).exportLargeExcelToZip(context, response);
            }
            default -> throw new IllegalArgumentException("未知导出模式");
        }
    }

    /**
     * 导入真实上传文件并读取事务提交结果，成功和失败都清理本次随机 run_id 数据。
     *
     * @param file 上传文件
     * @param mode simple 或 large
     * @param sheet Sheet 序号
     * @param batchSize 批次大小
     * @param transactional 是否开启批次事务，仅用于 large
     * @param continueOnError 是否继续失败批次，仅用于 large
     * @param failAt 故意失败的合成编号，-1 表示不失败
     * @return 实际存储数据及进度
     * @throws Exception 导入失败时抛出
     */
    public ImportReport importFile(MultipartFile file, String mode, int sheet, int batchSize,
                                    boolean transactional, boolean continueOnError, int failAt) throws Exception {
        String run = UUID.randomUUID().toString();
        List<Progress> progress = new ArrayList<>();
        try {
            ExcelTemplate.ImportResult result;
            if ("simple".equals(mode)) {
                ImportContext<ExcelRow> context = new ImportContext<>();
                context.setFile(file);
                context.setEntityClass(ExcelRow.class);
                context.setSheetNo(sheet);
                context.setBatchCount(batchSize);
                new SimpleProcessor(executor, jdbc, run).importExcel(context);
                Long total = jdbc.queryForObject("SELECT COUNT(*) FROM excel_example_import WHERE run_id=?",
                        Long.class, run);
                result = new ExcelTemplate.ImportResult(total, 0);
            } else if ("large".equals(mode)) {
                LargeDataImportContext<ExcelRow> context = new LargeDataImportContext<>();
                context.setUniqueId(run);
                context.setFile(file);
                context.setEntityClass(ExcelRow.class);
                context.setSheetNo(sheet);
                context.setBatchSize(batchSize);
                context.setThreadCount(2);
                context.setQueueSize(1);
                context.setEnableTransaction(transactional);
                context.setContinueOnError(continueOnError);
                context.setProgressCallback((current, total, success, failed) ->
                        progress.add(new Progress(current, total, success, failed)));
                result = new DatabaseImporter(executor, transactions, jdbc, failAt).importLargeExcelWithResult(context);
            } else {
                throw new IllegalArgumentException("未知导入模式");
            }
            List<StoredRow> stored = jdbc.query(
                    "SELECT id, name FROM excel_example_import WHERE run_id=? ORDER BY id",
                    (row, index) -> new StoredRow(row.getInt("id"), row.getString("name")), run);
            return new ImportReport(result.total(), result.success(), result.failed(), stored, List.copyOf(progress));
        } finally {
            jdbc.update("DELETE FROM excel_example_import WHERE run_id=?", run);
        }
    }

    /**
     * 查询真实数据库和临时目录，用于自动化验证没有残留数据。
     *
     * @return 当前残留数量
     * @throws IOException 目录读取失败时抛出
     */
    public Map<String, Long> state() throws IOException {
        try (var files = Files.list(directory)) {
            Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM excel_example_import", Long.class);
            return Map.of("rows", rows, "temporaryFiles", files.count());
        }
    }

    /**
     * 退出时清理仅属于本示例的临时根目录，不访问用户业务文件。
     *
     * @throws IOException 清理失败时抛出
     */
    @PreDestroy
    public void destroy() throws IOException {
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * 设置兼容上下文的实际查询和文件参数。
     *
     * @param context 上下文
     * @param count 合成总数
     * @param pageSize 分页大小
     */
    private void fill(ExportContext<ExcelRow, Integer> context, int count, int pageSize) {
        context.setEntityClass(ExcelRow.class);
        context.setQueryParams(count);
        context.setFileName("legacy-data");
        context.setSheetName("legacy");
        context.setPageSize(pageSize);
    }

    /**
     * 构造确定性合成分页，所有引擎都应输出相同编号和中文名称。
     *
     * @param page 一基页码
     * @param size 分页大小
     * @param count 总数
     * @return 当前页数据
     */
    private static List<ExcelRow> rows(int page, int size, int count) {
        int start = (page - 1) * size;
        return IntStream.range(start + 1, Math.min(count, start + size) + 1).mapToObj(index -> {
            ExcelRow row = new ExcelRow();
            row.setId(index);
            row.setName("名称" + index);
            return row;
        }).toList();
    }

    /**
     * 使用参数化 SQL 写入当前请求的数据，并可在插入后故意失败以验证回滚。
     *
     * @param jdbc 独立数据库
     * @param run 随机运行标识
     * @param rows 数据批次
     * @param failAt 失败编号，-1 为不失败
     */
    private static void insert(JdbcTemplate jdbc, String run, List<ExcelRow> rows, int failAt) {
        for (ExcelRow row : rows) {
            Assert.notNull(row.getId(), "编号不能为空");
            jdbc.update("INSERT INTO excel_example_import (run_id, id, name) VALUES (?, ?, ?)",
                    run, row.getId(), row.getName());
            if (row.getId() == failAt) {
                throw new IllegalStateException("模拟业务批次失败");
            }
        }
    }

    /**
     * 普通处理器(SimpleProcessor)实现原单次导出和批次导入业务回调。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    private static class SimpleProcessor extends AbstractSimpleExcelProcessor<ExcelRow, Integer> {

        /**
         * 独立数据库。
         */
        private final JdbcTemplate jdbc;

        /**
         * 导入请求标识，导出时为 null。
         */
        private final String run;

        /**
         * 注入业务依赖。
         *
         * @param executor 执行器
         * @param jdbc 数据库
         * @param run 请求标识
         */
        SimpleProcessor(ExecutorService executor, JdbcTemplate jdbc, String run) {
            super(executor);
            this.jdbc = jdbc;
            this.run = run;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleImportData(List<ExcelRow> rows, ImportContext context) {
            insert(jdbc, run, rows, -1);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ExcelRow> getExportData(ExportContext<ExcelRow, Integer> context) {
            return rows(1, context.getQueryParams(), context.getQueryParams());
        }
    }

    /**
     * 多 Sheet 查询回调(SheetExporter)不自行创建线程或操作工作簿。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    private static class SheetExporter extends MultiSheetExcelExporter<ExcelRow, Integer> {

        /**
         * 注入执行器。
         *
         * @param executor 执行器
         */
        SheetExporter(ExecutorService executor) {
            super(executor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ExcelRow> getExportData(MultiSheetExportContext<ExcelRow, Integer> context) {
            return rows(context.getCurrentPage(), context.getPageSize(), context.getQueryParams());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Long getTotalCount(MultiSheetExportContext<ExcelRow, Integer> context) {
            return context.getQueryParams().longValue();
        }
    }

    /**
     * ZIP 查询回调(ZipExporter)验证 fetchSize 到 pageSize 的兼容适配。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    private static class ZipExporter extends LargeExcelZipExporter<ExcelRow, Integer> {

        /**
         * 注入执行器。
         *
         * @param executor 执行器
         */
        ZipExporter(ExecutorService executor) {
            super(executor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ExcelRow> getExportData(LargeExcelZipExportContext<ExcelRow, Integer> context) {
            return rows(context.getCurrentPage(), context.getPageSize(), context.getQueryParams());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Long getTotalCount(LargeExcelZipExportContext<ExcelRow, Integer> context) {
            return context.getQueryParams().longValue();
        }
    }

    /**
     * 真实数据库导入回调(DatabaseImporter)仅负责 SQL，批次事务由 Starter 包围执行。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    private static class DatabaseImporter extends LargeDataExcelImporter<ExcelRow> {

        /**
         * 独立数据库。
         */
        private final JdbcTemplate jdbc;

        /**
         * 故意失败的行号。
         */
        private final int failAt;

        /**
         * 注入实际执行器和事务管理器。
         *
         * @param executor 执行器
         * @param transactions 事务管理器
         * @param jdbc 数据库
         * @param failAt 失败行号
         */
        DatabaseImporter(ExecutorService executor, PlatformTransactionManager transactions, JdbcTemplate jdbc, int failAt) {
            super(executor, transactions);
            this.jdbc = jdbc;
            this.failAt = failAt;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleImportData(List<ExcelRow> rows, LargeDataImportContext<ExcelRow> context) {
            insert(jdbc, context.getUniqueId(), rows, failAt);
        }
    }

    /**
     * 已存行(StoredRow)用于逐项核对实际导入内容。
     *
     * @param id 行编号
     * @param name 中文名称
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    public record StoredRow(
            /**
             * 实际存储的编号。
             */
            int id,

            /**
             * 实际存储的中文名称。
             */
            String name) {
    }

    /**
     * 进度快照(Progress)保持 current=success+failed。
     *
     * @param current 已处理数
     * @param total 已读取总数，读取中为 -1
     * @param success 成功批次行数
     * @param failed 失败批次行数
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    public record Progress(
            /**
             * 已处理数。
             */
            long current,

            /**
             * 总行数，读取中为 -1。
             */
            long total,

            /**
             * 成功行数。
             */
            long success,

            /**
             * 失败行数。
             */
            long failed) {
    }

    /**
     * 导入报告(ImportReport)返回批次统计与数据库真实内容，不暴露引擎内部对象。
     *
     * @param total 已处理总数
     * @param success 成功数
     * @param failed 失败数
     * @param stored 实际存储行
     * @param progress 有序进度
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    public record ImportReport(
            /**
             * 已处理总行数。
             */
            long total,

            /**
             * 成功批次行数。
             */
            long success,

            /**
             * 失败批次行数。
             */
            long failed,

            /**
             * 数据库实际存储的行。
             */
            List<StoredRow> stored,

            /**
             * 调用线程归集的有序进度快照。
             */
            List<Progress> progress) {
    }
}
