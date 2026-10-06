package io.github.bytex0.excel;

import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.read.listener.PageReadListener;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.apache.fesod.sheet.write.handler.WorkbookWriteHandler;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.fesod.sheet.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.springframework.util.Assert;

import java.io.FilterOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Excel(ExcelTemplate)有界批次导入、多Sheet及ZIP流式导出
 *
 * @author bytex0
 * @since 2026-10-05 15:36:55
 */
public class ExcelTemplate {

    /**
     * 单批上限，避免供应者意外一次返回完整数据集
     */
    public static final int MAX_BATCH_ROWS = 10000;

    /**
     * 每Sheet数据行上限，预留表头空间
     */
    public static final int MAX_SHEET_ROWS = 1_000_000;

    /**
     * 使用原模板的带序号名称流式写出，不关闭调用方输出流。
     *
     * @param output 输出流
     * @param type 行类型
     * @param sheetName 基础名称
     * @param source 有界批次来源，空列表表示结束
     * @param rowsPerSheet 每 Sheet 最大数据行数
     * @param <T> 行类型
     */
    public <T> void write(OutputStream output, Class<T> type, String sheetName,
                          Supplier<List<T>> source, int rowsPerSheet) {
        writeNamed(output, type, index -> safeName(sheetName, index), source, rowsPerSheet);
    }

    /**
     * 按一基序号指定 Sheet 名，保留兼容处理器原命名规则并跨批次拆分。
     *
     * @param output 调用方拥有的输出流
     * @param type 行类型
     * @param names 一基序号到名称的函数
     * @param source 单批最多 10000 行，空批表示结束
     * @param rowsPerSheet 每 Sheet 数据行上限，范围 1 至 1000000
     * @param <T> 行类型
     */
    public <T> void writeNamed(OutputStream output, Class<T> type, IntFunction<String> names,
                               Supplier<List<T>> source, int rowsPerSheet) {
        validate(output, type, source, rowsPerSheet);
        Assert.notNull(names, "Sheet命名函数不能为空");
        BatchCursor<T> cursor = new BatchCursor<>(source);
        Set<String> usedNames = new HashSet<>();
        try (ExcelWriter writer = writer(output, type)) {
            int index = 0;
            do {
                String name = sheetName(names.apply(index + 1));
                Assert.isTrue(usedNames.add(name.toLowerCase(Locale.ROOT)), "规范化后的Sheet名称不能重复");
                WriteSheet sheet = FesodSheet.writerSheet(index++, name).build();
                writer.write(List.of(), sheet);
                writeRows(writer, sheet, cursor, rowsPerSheet);
            } while (cursor.hasNext());
        }
    }

    /**
     * 使用原 data-N.xlsx 命名输出 ZIP，不关闭调用方流。
     *
     * @param output 输出流
     * @param type 行类型
     * @param source 有界批次来源
     * @param rowsPerFile 每文件的数据行上限
     * @param <T> 行类型
     * @throws IOException 临时文件或 ZIP 输出失败时抛出
     */
    public <T> void writeZip(OutputStream output, Class<T> type, Supplier<List<T>> source,
                             int rowsPerFile) throws IOException {
        writeZipNamed(output, type, source, rowsPerFile, null,
                index -> "data-" + index + ".xlsx", index -> "Data");
    }

    /**
     * 指定临时目录、文件和 Sheet 名，始终只保留一个本次临时工作簿并在失败时清理。
     *
     * @param output 调用方输出流
     * @param type 行类型
     * @param source 有界批次来源
     * @param rowsPerFile 每文件数据行上限
     * @param directory 临时根目录，null 表示系统目录，不删除该根目录
     * @param fileNames 一基文件名称函数，条目名称必须为单文件名
     * @param sheetNames 一基 Sheet 名称函数
     * @param <T> 行类型
     * @throws IOException 临时文件或输出失败时抛出
     */
    public <T> void writeZipNamed(OutputStream output, Class<T> type, Supplier<List<T>> source, int rowsPerFile,
                                  Path directory, IntFunction<String> fileNames,
                                  IntFunction<String> sheetNames) throws IOException {
        validate(output, type, source, rowsPerFile);
        Assert.notNull(fileNames, "ZIP命名函数不能为空");
        Assert.notNull(sheetNames, "Sheet命名函数不能为空");
        BatchCursor<T> cursor = new BatchCursor<>(source);
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Path temporary = directory == null ? Files.createTempFile("common-tool-excel-", ".xlsx")
                : Files.createTempFile(directory, "common-tool-excel-", ".xlsx");
        try (TemporaryWorkbook workbookFile = new TemporaryWorkbook(temporary);
             ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            int index = 0;
            Set<String> entries = new HashSet<>();
            do {
                String name = fileNames.apply(++index);
                Assert.hasText(name, "ZIP条目名称不能为空");
                Assert.isTrue(!name.contains("/") && !name.contains("\\") && !name.contains("\0")
                                && !".".equals(name) && !"..".equals(name),
                        "ZIP条目不能包含路径或零字符");
                Assert.isTrue(entries.add(name), "ZIP条目名称不能重复");
                try (OutputStream file = Files.newOutputStream(workbookFile.path()); ExcelWriter writer = writer(file, type)) {
                    WriteSheet sheet = FesodSheet.writerSheet(sheetName(sheetNames.apply(index))).build();
                    writer.write(List.of(), sheet);
                    writeRows(writer, sheet, cursor, rowsPerFile);
                }
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(workbookFile.path(), zip);
                zip.closeEntry();
            } while (cursor.hasNext());
        }
    }

    /**
     * 顺序有界批次导入。调用方负责事务，失败默认传播；继续模式返回失败行数。
     *
     * @param input 调用方拥有的输入流
     * @param type 行类型
     * @param sheetNo 零基 Sheet 序号
     * @param batchSize 批次大小，范围 1 至 10000
     * @param consumer 批次回调
     * @param continueOnError 是否继续处理业务批次失败，解析失败始终传播
     * @param <T> 行类型
     * @return 成功/失败批次的行数
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
        readWithListener(input, type, sheetNo, listener);
        return new ImportResult(counts[0], counts[1]);
    }

    /**
     * 用成品读取引擎执行监听器，按文件签名识别 XLS/XLSX，不把任意文本当成空 CSV。
     *
     * @param input 调用方拥有的输入流
     * @param type 行类型
     * @param sheetNo 零基 Sheet 序号
     * @param listener Apache Fesod 读取监听器
     * @param <T> 行类型
     */
    public <T> void readWithListener(InputStream input, Class<T> type, int sheetNo, ReadListener<T> listener) {
        Assert.notNull(input, "输入流不能为空");
        Assert.notNull(type, "行类型不能为空");
        Assert.notNull(listener, "读取监听器不能为空");
        Assert.isTrue(sheetNo >= 0, "Sheet序号不能为负数");
        try {
            InputStream checked = FileMagic.prepareToCheckMagic(new NonClosingInputStream(input));
            ExcelTypeEnum format = switch (FileMagic.valueOf(checked)) {
                case OLE2 -> ExcelTypeEnum.XLS;
                case OOXML -> ExcelTypeEnum.XLSX;
                default -> throw new IllegalArgumentException("仅支持具有合法文件签名的XLS或XLSX");
            };
            FesodSheet.read(checked, type, listener).excelType(format)
                    .autoCloseStream(false).sheet(sheetNo).doRead();
        } catch (IOException exception) {
            throw new UncheckedIOException("读取Excel文件签名失败", exception);
        }
    }

    /**
     * 写出当前 Sheet 的剩余容量，多出的批次行留给下一 Sheet。
     *
     * @param writer 工作簿写入器
     * @param sheet 当前 Sheet
     * @param cursor 有界游标
     * @param limit 行上限
     * @param <T> 行类型
     */
    private <T> void writeRows(ExcelWriter writer, WriteSheet sheet, BatchCursor<T> cursor, int limit) {
        int rows = 0;
        while (rows < limit && cursor.hasNext()) {
            List<T> chunk = cursor.take(limit - rows);
            writer.write(chunk, sheet);
            rows += chunk.size();
        }
    }

    /**
     * 构造资源作用域内的流式写入器，压缩临时 XML，但不拥有外部输出流。
     *
     * @param output 输出流
     * @param type 行类型
     * @param <T> 行类型
     * @return 调用方负责关闭的写入器
     */
    private <T> ExcelWriter writer(OutputStream output, Class<T> type) {
        return FesodSheet.write(new NonClosingOutputStream(output), type).autoCloseStream(false)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .registerWriteHandler(new WorkbookWriteHandler() {
                    /**
                     * 使用流式工作簿时压缩其临时文件。
                     *
                     * @param holder 工作簿持有者
                     */
                    @Override
                    public void afterWorkbookCreate(WriteWorkbookHolder holder) {
                        if (holder.getWorkbook() instanceof SXSSFWorkbook workbook) {
                            workbook.setCompressTempFiles(true);
                        }
                    }
                }).build();
    }

    /**
     * 在创建资源前校验基础参数。
     *
     * @param output 输出流
     * @param type 行类型
     * @param source 批次来源
     * @param rows 每 Sheet 行数
     */
    private void validate(OutputStream output, Class<?> type, Supplier<?> source, int rows) {
        Assert.notNull(output, "output 不能为空");
        Assert.notNull(type, "type 不能为空");
        Assert.notNull(source, "source 不能为空");
        Assert.isTrue(rows > 0 && rows <= MAX_SHEET_ROWS, "每Sheet或文件行数必须在1至1000000之间");
    }

    /**
     * 保持现有模板的基础名称加连字符序号，给序号保留空间。
     *
     * @param name 基础名
     * @param index 一基序号
     * @return 安全名称
     */
    private String safeName(String name, int index) {
        String base = name == null || name.isBlank() ? "Sheet" : name;
        String suffix = "-" + index;
        base = WorkbookUtil.createSafeSheetName(base);
        int end = Math.min(base.length(), 31 - suffix.length());
        if (end > 0 && Character.isHighSurrogate(base.charAt(end - 1))) {
            end--;
        }
        return base.substring(0, end) + suffix;
    }

    /**
     * 规范化显式 Sheet 名，并避免在 31 单元边界留下半个 Unicode 代理对。
     *
     * @param name 显式名称
     * @return 安全名称
     */
    private String sheetName(String name) {
        Assert.hasText(name, "Sheet名称不能为空");
        String safe = WorkbookUtil.createSafeSheetName(name);
        if (!safe.isEmpty() && Character.isHighSurrogate(safe.charAt(safe.length() - 1))) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    /**
     * 导入结果(ImportResult)成功与失败行计数
     *
     * @author bytex0
     * @since 2026-10-05 15:36:55
     * @param success 成功行数
     * @param failed 失败行数
     */
    public record ImportResult(
            /**
             * 成功批次的行数。
             */
            long success,

            /**
             * 失败批次的行数，不推断事务关闭时可能产生的部分业务写入。
             */
            long failed) {

        /**
         * 获取已处理总行数。
         *
         * @return 成功与失败之和
         */
        public long total() {
            return success + failed;
        }
    }

    /**
     * 批次游标(BatchCursor)跨Sheet边界拆分，始终仅保留一个批次
     *
     * @author bytex0
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

        /**
         * 创建单批游标。
         *
         * @param source 批次来源
         */
        BatchCursor(Supplier<List<T>> source) {
            this.source = source;
        }

        /**
         * 必要时获取下一批次，空列表明确结束。
         *
         * @return 是否还有数据
         */
        boolean hasNext() {
            if (position == batch.size() && !ended) {
                List<T> next = Objects.requireNonNull(source.get(), "数据供应者不能返回null，请用空列表表示结束");
                Assert.isTrue(next.size() <= MAX_BATCH_ROWS, "单批数据不能超过10000行");
                batch = List.copyOf(next);
                position = 0;
                ended = batch.isEmpty();
            }
            return !ended;
        }

        /**
         * 返回当前批次的有限视图，只在本次同步写入期间使用。
         *
         * @param limit 最多行数
         * @return 批次片段
         */
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
     * @author bytex0
     * @since 2026-10-05 15:36:55
     */
    private static class NonClosingOutputStream extends FilterOutputStream {

        /**
         * 创建不转移底层流所有权的包装器。
         *
         * @param output 底层输出流
         */
        NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        /**
         * 只刷新而不关闭调用方资源。
         *
         * @throws IOException 刷新失败时抛出
         */
        @Override
        public void close() throws IOException {
            flush();
        }

        /**
         * 直接批量写入，避免 FilterOutputStream 默认逐字节写入。
         *
         * @param bytes 数据
         * @param offset 起点
         * @param length 长度
         * @throws IOException 写入失败时抛出
         */
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }
    }

    /**
     * 输入流保护(NonClosingInputStream)隔离 XLS 底层文件系统的自动关闭行为。
     *
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    private static class NonClosingInputStream extends FilterInputStream {

        /**
         * 借用调用方输入流，不接管关闭责任。
         *
         * @param input 调用方输入流
         */
        NonClosingInputStream(InputStream input) {
            super(input);
        }

        /**
         * 此包装器没有独立资源，关闭不传播到调用方输入流。
         */
        @Override
        public void close() {
            // 底层 XLS 解析器可能无视 autoCloseStream，必须在此隔离关闭。
        }
    }

    /**
     * 临时工作簿(TemporaryWorkbook)通过资源作用域清理，保留原始异常及可能的清理异常。
     *
     * @param path 本次创建的临时文件
     * @author linshiqiang
     * @since 2026-10-06 11:23:54
     */
    private record TemporaryWorkbook(
            /**
             * 本次操作拥有的唯一临时文件。
             */
            Path path) implements AutoCloseable {

        /**
         * 删除本次临时文件，失败由 try-with-resources 自动保留为抑制异常。
         *
         * @throws IOException 删除失败时抛出
         */
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
