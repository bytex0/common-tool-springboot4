package io.github.bytex0.excel;

import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.read.listener.PageReadListener;
import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.apache.fesod.sheet.write.handler.WorkbookWriteHandler;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.fesod.sheet.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.util.Assert;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Excel(ExcelTemplate)有界批次导入、多Sheet及ZIP流式导出
 *
 * @author linshiqiang
 * @since 2026-10-05 15:36:55
 */
public class ExcelTemplate {

    /**
     * 单批上限，避免供应者意外一次返回完整数据集
     */
    private static final int MAX_BATCH_ROWS = 10000;

    /**
     * 每Sheet数据行上限，预留表头空间
     */
    private static final int MAX_SHEET_ROWS = 1_000_000;

    public <T> void write(OutputStream output, Class<T> type, String sheetName,
                          Supplier<List<T>> source, int rowsPerSheet) {
        validate(output, type, source, rowsPerSheet);
        BatchCursor<T> cursor = new BatchCursor<>(source);
        try (ExcelWriter writer = writer(output, type)) {
            int index = 0;
            do {
                WriteSheet sheet = FesodSheet.writerSheet(index, safeName(sheetName, ++index)).build();
                writer.write(List.of(), sheet);
                writeRows(writer, sheet, cursor, rowsPerSheet);
            } while (cursor.hasNext());
        }
    }

    public <T> void writeZip(OutputStream output, Class<T> type, Supplier<List<T>> source,
                             int rowsPerFile) throws IOException {
        validate(output, type, source, rowsPerFile);
        BatchCursor<T> cursor = new BatchCursor<>(source);
        Path temporary = Files.createTempFile("common-tool-excel-", ".xlsx");
        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            int index = 0;
            do {
                try (OutputStream file = Files.newOutputStream(temporary); ExcelWriter writer = writer(file, type)) {
                    WriteSheet sheet = FesodSheet.writerSheet("Data").build();
                    writer.write(List.of(), sheet);
                    writeRows(writer, sheet, cursor, rowsPerFile);
                }
                zip.putNextEntry(new ZipEntry("data-" + (++index) + ".xlsx"));
                Files.copy(temporary, zip);
                zip.closeEntry();
            } while (cursor.hasNext());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 顺序有界批次导入。调用方负责事务，失败默认传播；继续模式返回失败行数。
     */
    public <T> ImportResult read(InputStream input, Class<T> type, int sheetNo, int batchSize,
                                  Consumer<List<T>> consumer, boolean continueOnError) {
        Assert.notNull(input, "input 不能为空");
        Assert.notNull(type, "type 不能为空");
        Assert.notNull(consumer, "consumer 不能为空");
        Assert.isTrue(sheetNo >= 0, "sheetNo 不能为负数");
        Assert.isTrue(batchSize > 0 && batchSize <= MAX_BATCH_ROWS, "batchSize 必须在1至10000之间");
        long[] counts = new long[2];
        PageReadListener<T> listener = new PageReadListener<>(batch -> {
            try {
                consumer.accept(List.copyOf(batch));
                counts[0] += batch.size();
            } catch (RuntimeException exception) {
                counts[1] += batch.size();
                if (!continueOnError) {
                    throw exception;
                }
            }
        }, batchSize);
        FesodSheet.read(input, type, listener).excelType(ExcelTypeEnum.XLSX)
                .autoCloseStream(false).sheet(sheetNo).doRead();
        return new ImportResult(counts[0], counts[1]);
    }

    private <T> void writeRows(ExcelWriter writer, WriteSheet sheet, BatchCursor<T> cursor, int limit) {
        int rows = 0;
        while (rows < limit && cursor.hasNext()) {
            List<T> chunk = cursor.take(limit - rows);
            writer.write(chunk, sheet);
            rows += chunk.size();
        }
    }

    private <T> ExcelWriter writer(OutputStream output, Class<T> type) {
        return FesodSheet.write(output, type).autoCloseStream(false)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .registerWriteHandler(new WorkbookWriteHandler() {
                    @Override
                    public void afterWorkbookCreate(WriteWorkbookHolder holder) {
                        if (holder.getWorkbook() instanceof SXSSFWorkbook workbook) {
                            workbook.setCompressTempFiles(true);
                        }
                    }
                }).build();
    }

    private void validate(OutputStream output, Class<?> type, Supplier<?> source, int rows) {
        Assert.notNull(output, "output 不能为空");
        Assert.notNull(type, "type 不能为空");
        Assert.notNull(source, "source 不能为空");
        Assert.isTrue(rows > 0 && rows <= MAX_SHEET_ROWS, "每Sheet或文件行数必须在1至1000000之间");
    }

    private String safeName(String name, int index) {
        String base = name == null || name.isBlank() ? "Sheet" : name;
        String suffix = "-" + index;
        base = WorkbookUtil.createSafeSheetName(base);
        return base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
    }

    /**
     * 导入结果(ImportResult)成功与失败行计数
     *
     * @author linshiqiang
     * @since 2026-10-05 15:36:55
     * @param success 成功行数
     * @param failed 失败行数
     */
    public record ImportResult(long success, long failed) {
        public long total() {
            return success + failed;
        }
    }

    /**
     * 批次游标(BatchCursor)跨Sheet边界拆分，始终仅保留一个批次
     *
     * @author linshiqiang
     * @since 2026-10-05 15:36:55
     */
    private static class BatchCursor<T> {

        /**
         * 批次来源，空列表表示结束
         */
        private final Supplier<List<T>> source;

        /**
         * 当前批次
         */
        private List<T> batch = List.of();

        /**
         * 当前批次游标
         */
        private int position;

        /**
         * 是否读取结束
         */
        private boolean ended;

        BatchCursor(Supplier<List<T>> source) {
            this.source = source;
        }

        boolean hasNext() {
            if (position == batch.size() && !ended) {
                batch = Objects.requireNonNull(source.get(), "数据供应者不能返回null，请用空列表表示结束");
                Assert.isTrue(batch.size() <= MAX_BATCH_ROWS, "单批数据不能超过10000行");
                position = 0;
                ended = batch.isEmpty();
            }
            return !ended;
        }

        List<T> take(int limit) {
            int end = Math.min(batch.size(), position + limit);
            List<T> chunk = batch.subList(position, end);
            position = end;
            return chunk;
        }
    }

    /**
     * 输出流(NonClosingOutputStream)允许关闭ZIP而不关闭调用方流
     *
     * @author linshiqiang
     * @since 2026-10-05 15:36:55
     */
    private static class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            flush();
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }
    }
}
