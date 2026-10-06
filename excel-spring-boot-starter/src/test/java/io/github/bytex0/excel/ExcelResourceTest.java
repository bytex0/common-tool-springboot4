package io.github.bytex0.excel;

import io.github.bytex0.excel.core.exporter.LargeExcelZipExportContext;
import io.github.bytex0.excel.core.exporter.LargeExcelZipExporter;
import io.github.bytex0.excel.util.ExcelUtil;
import org.apache.fesod.sheet.read.listener.PageReadListener;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Excel 资源测试(ExcelResourceTest)验证旧 XLS、静态工具和 ZIP 临时文件所有权。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:15:35
 */
class ExcelResourceTest {

    /**
     * 本次测试独有的临时根目录。
     */
    @TempDir
    private Path directory;

    /**
     * 识别合法 XLS，不把普通文本当成成功导入；调用方输入流不被模板关闭。
     *
     * @throws Exception 工作簿操作失败时抛出
     */
    @Test
    void shouldReadXlsAndKeepCallerStreamOpen() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            var sheet = workbook.createSheet("Xls");
            sheet.createRow(0).createCell(0).setCellValue("ID");
            sheet.createRow(1).createCell(0).setCellValue(42);
            workbook.write(data);
        }
        List<Integer> ids = new ArrayList<>();
        try (TrackedInput input = new TrackedInput(data.toByteArray())) {
            new ExcelTemplate().read(input, ExcelTemplateTest.Row.class, 0, 2,
                    rows -> rows.forEach(row -> ids.add(row.getId())), false);
            assertThat(input.closed).isFalse();
        }
        assertThat(ids).containsExactly(42);
        assertThatThrownBy(() -> new ExcelTemplate().read(new ByteArrayInputStream(new byte[]{1, 2, 3}),
                ExcelTemplateTest.Row.class, 0, 1, rows -> { }, false)).isInstanceOf(RuntimeException.class);
    }

    /**
     * 原 ExcelUtil 静态方法可往返，上传流由工具关闭。
     *
     * @throws Exception 文件或序列化失败时抛出
     */
    @Test
    void shouldRetainStaticUtilityAndCloseOwnedUploadStream() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExcelUtil.exportToExcel(List.of(row(7)), "utility", response, ExcelTemplateTest.Row.class);
        TrackedFile file = new TrackedFile(response.getContentAsByteArray());
        List<Integer> ids = new ArrayList<>();
        ExcelUtil.importFromExcel(file, ExcelTemplateTest.Row.class,
                new PageReadListener<>(rows -> rows.forEach(row -> ids.add(row.getId())), 2));
        assertThat(ids).containsExactly(7);
        assertThat(file.opened.closed).isTrue();
    }

    /**
     * ZIP 分页结果按顺序精确拆分，unsafe 文件名和 uniqueId 不能逃逸临时目录。
     *
     * @throws Exception 导出或解析失败时抛出
     */
    @Test
    void shouldSplitZipAndCleanTemporaryFiles() throws Exception {
        try (ExecutorService executor = executor()) {
            LargeExcelZipExportContext<ExcelTemplateTest.Row, Void> context = context();
            MockHttpServletResponse response = new MockHttpServletResponse();
            new ZipExporter(executor, false).exportLargeExcelToZip(context, response);
            List<Integer> ids = new ArrayList<>();
            int count = 0;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertThat(entry.getName()).isEqualTo("report_" + (++count) + ".xlsx");
                    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(zip.readAllBytes()))) {
                        var sheet = workbook.getSheetAt(0);
                        assertThat(sheet.getLastRowNum()).isLessThanOrEqualTo(5);
                        for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                            ids.add((int) sheet.getRow(row).getCell(0).getNumericCellValue());
                        }
                    }
                }
            }
            assertThat(count).isEqualTo(3);
            assertThat(ids).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
            assertThat(context.getPageSize()).isEqualTo(1000);
            assertEmptyDirectory();
        }
    }

    /**
     * 生成中途失败也清理工作簿，不仅在 ZIP 完成后清理。
     *
     * @throws Exception 文件系统检查失败时抛出
     */
    @Test
    void shouldCleanZipWhenPrefetchFails() throws Exception {
        try (ExecutorService executor = executor()) {
            assertThatThrownBy(() -> new ZipExporter(executor, true)
                    .exportLargeExcelToZip(context(), new MockHttpServletResponse())).isInstanceOf(RuntimeException.class);
            assertEmptyDirectory();
        }
    }

    /**
     * 创建带越界路径标识的测试上下文，标识不得用于拼接目录。
     *
     * @return ZIP 参数
     */
    private LargeExcelZipExportContext<ExcelTemplateTest.Row, Void> context() {
        LargeExcelZipExportContext<ExcelTemplateTest.Row, Void> context = new LargeExcelZipExportContext<>();
        context.setEntityClass(ExcelTemplateTest.Row.class);
        context.setFileName("../../report\r\n");
        context.setUniqueId("../not-a-directory");
        context.setTempDir(directory.toString());
        context.setMaxRowsPerSheet(5);
        context.setFetchSize(4);
        return context;
    }

    /**
     * 检查没有遗留本次临时工作簿。
     *
     * @throws IOException 目录枚举失败时抛出
     */
    private void assertEmptyDirectory() throws IOException {
        try (var entries = Files.list(directory)) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    /**
     * 创建有限测试执行器。
     *
     * @return 需要关闭的执行器
     */
    private ExecutorService executor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4),
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
     * ZIP 测试导出器(ZipExporter)模拟正常分页或第二页失败。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:15:35
     */
    private static class ZipExporter extends LargeExcelZipExporter<ExcelTemplateTest.Row, Void> {

        /**
         * 是否故意使第二页失败。
         */
        private final boolean fail;

        /**
         * 注入执行器和测试模式。
         *
         * @param executor 执行器
         * @param fail 失败模式
         */
        ZipExporter(ExecutorService executor, boolean fail) {
            super(executor);
            this.fail = fail;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ExcelTemplateTest.Row> getExportData(LargeExcelZipExportContext<ExcelTemplateTest.Row, Void> context) {
            if (fail && context.getCurrentPage() == 2) {
                throw new IllegalStateException("expected query failure");
            }
            int start = (context.getCurrentPage() - 1) * context.getPageSize();
            return IntStream.range(start + 1, Math.min(11, start + context.getPageSize()) + 1)
                    .mapToObj(ExcelResourceTest::row).toList();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Long getTotalCount(LargeExcelZipExportContext<ExcelTemplateTest.Row, Void> context) {
            return 11L;
        }
    }

    /**
     * 跟踪输入(TrackedInput)检测调用方与组件的关闭责任。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:15:35
     */
    private static class TrackedInput extends ByteArrayInputStream {

        /**
         * 是否执行了 close。
         */
        private boolean closed;

        /**
         * 包装测试内容。
         *
         * @param data 内容
         */
        TrackedInput(byte[] data) {
            super(data);
        }

        /**
         * 记录关闭。
         *
         * @throws IOException 底层关闭失败时抛出
         */
        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /**
     * 上传文件替身(TrackedFile)保留最近打开的输入流以验证关闭。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:15:35
     */
    private static class TrackedFile extends MockMultipartFile {

        /**
         * 本次上传读取流。
         */
        private TrackedInput opened;

        /**
         * 包装独立测试工作簿。
         *
         * @param data 内容
         */
        TrackedFile(byte[] data) {
            super("file", "input.xlsx", null, data);
        }

        /**
         * 创建可跟踪读取流。
         *
         * @return 测试输入流
         * @throws IOException 读取测试文件字节失败时抛出
         */
        @Override
        public TrackedInput getInputStream() throws IOException {
            opened = new TrackedInput(getBytes());
            return opened;
        }
    }
}
