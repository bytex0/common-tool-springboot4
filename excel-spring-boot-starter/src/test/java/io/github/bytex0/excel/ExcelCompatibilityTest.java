package io.github.bytex0.excel;

import io.github.bytex0.excel.core.AbstractSimpleExcelProcessor;
import io.github.bytex0.excel.core.exporter.ExportContext;
import io.github.bytex0.excel.core.exporter.MultiSheetExcelExporter;
import io.github.bytex0.excel.core.exporter.MultiSheetExportContext;
import io.github.bytex0.excel.core.importer.ImportContext;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel 兼容测试(ExcelCompatibilityTest)通过原处理器入口验证响应流和 Sheet 选择。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
class ExcelCompatibilityTest {

    /**
     * 页大小与 Sheet 上限不整除时仍保持精确顺序，并且不修改调用方分页上下文。
     *
     * @throws Exception 导出或工作簿解析失败时抛出
     */
    @Test
    void shouldPreservePagedExportOrderAndSheetLimits() throws Exception {
        try (ExecutorService executor = executor()) {
            MultiSheetExportContext<ExcelTemplateTest.Row, Void> context = new MultiSheetExportContext<>();
            context.setEntityClass(ExcelTemplateTest.Row.class);
            context.setFileName("pages");
            context.setSheetName("Original");
            context.setPageSize(4);
            context.setMaxRowsPerSheet(5);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new PagedExporter(executor).exportMultiSheetExcel(context, response);
            List<Integer> ids = new ArrayList<>();
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
                for (int index = 0; index < 3; index++) {
                    var sheet = workbook.getSheetAt(index);
                    assertThat(sheet.getSheetName()).isEqualTo("Original_" + (index + 1));
                    assertThat(sheet.getLastRowNum()).isLessThanOrEqualTo(5);
                    for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                        ids.add((int) sheet.getRow(row).getCell(0).getNumericCellValue());
                    }
                }
            }
            assertThat(ids).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
            assertThat(context.getCurrentPage()).isEqualTo(1);
        }
    }

    /**
     * 普通导出必须写入 HTTP 响应，普通导入必须读取指定 Sheet。
     *
     * @throws Exception 工作簿解析或处理失败时抛出
     */
    @Test
    void shouldWriteResponseAndReadSelectedSheet() throws Exception {
        try (ExecutorService executor = executor()) {
            SimpleProcessor processor = new SimpleProcessor(executor);
            ExportContext<ExcelTemplateTest.Row, Void> context = new ExportContext<>();
            context.setFileName("report");
            context.setSheetName("original");
            context.setEntityClass(ExcelTemplateTest.Row.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            processor.exportExcel(context, response);
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("original");
                assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue()).isEqualTo(7);
            }
            ByteArrayOutputStream source = new ByteArrayOutputStream();
            new ExcelTemplate().write(source, ExcelTemplateTest.Row.class, "Data",
                    new Supplier<>() {
                        /**
                         * 只生成一个批次。
                         */
                        private boolean sent;

                        /**
                         * 返回两个数据行后结束。
                         *
                         * @return 下一批
                         */
                        @Override
                        public List<ExcelTemplateTest.Row> get() {
                            if (sent) {
                                return List.of();
                            }
                            sent = true;
                            return List.of(row(1), row(2));
                        }
                    }, 1);
            ImportContext<ExcelTemplateTest.Row> input = new ImportContext<>();
            input.setFile(new MockMultipartFile("file", "data.xlsx", null, source.toByteArray()));
            input.setEntityClass(ExcelTemplateTest.Row.class);
            input.setSheetNo(1);
            processor.importExcel(input);
            assertThat(processor.imported).containsExactly(2);
        }
    }

    /**
     * 创建测试专用的有限执行器。
     *
     * @return 需要关闭的线程池
     */
    private ExecutorService executor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建合成行。
     *
     * @param id 编号
     * @return 行
     */
    private static ExcelTemplateTest.Row row(int id) {
        ExcelTemplateTest.Row row = new ExcelTemplateTest.Row();
        row.setId(id);
        return row;
    }

    /**
     * 普通处理器(SimpleProcessor)实现原业务回调，不复制读写引擎。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:57:29
     */
    private static class SimpleProcessor extends AbstractSimpleExcelProcessor<ExcelTemplateTest.Row, Void> {

        /**
         * 已导入的合成行号。
         */
        private final List<Integer> imported = new ArrayList<>();

        /**
         * 使用外部管理的执行器。
         *
         * @param executor 执行器
         */
        SimpleProcessor(ExecutorService executor) {
            super(executor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleImportData(List<ExcelTemplateTest.Row> rows, ImportContext context) {
            rows.forEach(row -> imported.add(row.getId()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ExcelTemplateTest.Row> getExportData(ExportContext<ExcelTemplateTest.Row, Void> context) {
            return List.of(row(7));
        }
    }

    /**
     * 分页导出器(PagedExporter)使用原 getTotalCount/getExportData 回调模拟数据库分页。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:06:59
     */
    private static class PagedExporter extends MultiSheetExcelExporter<ExcelTemplateTest.Row, Void> {

        /**
         * 注入共享执行器。
         *
         * @param executor 执行器
         */
        PagedExporter(ExecutorService executor) {
            super(executor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ExcelTemplateTest.Row> getExportData(MultiSheetExportContext<ExcelTemplateTest.Row, Void> context) {
            int start = (context.getCurrentPage() - 1) * context.getPageSize();
            return IntStream.range(start + 1, Math.min(11, start + context.getPageSize()) + 1)
                    .mapToObj(ExcelCompatibilityTest::row).toList();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Long getTotalCount(MultiSheetExportContext<ExcelTemplateTest.Row, Void> context) {
            return 11L;
        }
    }
}
