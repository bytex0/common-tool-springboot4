package io.github.bytex0.excel.core;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.importer.ImportContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Excel 兼容辅助(ExcelSupport)统一文件名、响应头、上传校验和列表批次，不保存共享状态。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
public final class ExcelSupport {

    /**
     * XLSX 下载的标准媒体类型。
     */
    public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * 工具类不允许实例化。
     */
    private ExcelSupport() {
    }

    /**
     * 设置可靠的 UTF-8 下载响应头，不关闭或提前获取响应流。
     *
     * @param response Servlet 响应
     * @param fileName 基础文件名
     * @param zip 是否为 ZIP 下载
     */
    public static void prepare(HttpServletResponse response, String fileName, boolean zip) {
        Assert.notNull(response, "HTTP响应不能为空");
        String name = fileName(fileName);
        String extension = zip ? ".zip" : ".xlsx";
        if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            name += extension;
        }
        response.setContentType(zip ? "application/zip" : XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8).build().toString());
    }

    /**
     * 将外部名称限制为单文件名，移除控制字符，不允许路径逃逸。
     *
     * @param value 原始文件名，不能为空
     * @return 安全基础名称
     */
    public static String fileName(String value) {
        Assert.hasText(value, "文件名不能为空");
        String path = value.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder safe = new StringBuilder();
        name.codePoints().filter(codePoint -> codePoint >= 32 && codePoint != 127).forEach(safe::appendCodePoint);
        String result = safe.toString().strip();
        return result.isEmpty() || ".".equals(result) || "..".equals(result) ? "export" : result;
    }

    /**
     * 生成兼容的一基 Sheet 名称，为序号保留长度，避免长基础名被截断后发生重复。
     *
     * @param base 基础名，可空
     * @param index 一基序号
     * @param numbered 是否连第一个 Sheet 也附加序号
     * @return 不超过 31 个 UTF-16 单元的名称
     */
    public static String sheetName(String base, int index, boolean numbered) {
        String name = base == null || base.isBlank() ? "Sheet" : base;
        String suffix = numbered || index > 1 ? "_" + index : "";
        if ((base == null || base.isBlank()) && numbered) {
            suffix = Integer.toString(index);
        }
        int end = Math.min(name.length(), 31 - suffix.length());
        if (end > 0 && Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end) + suffix;
    }

    /**
     * 校验文件导入上下文。
     *
     * @param context 上下文
     * @return 零基 Sheet 序号，原 null 默认视为 0
     */
    public static int validateImport(ImportContext<?> context) {
        Assert.notNull(context, "导入上下文不能为空");
        Assert.notNull(context.getFile(), "上传文件不能为空");
        Assert.notNull(context.getEntityClass(), "行类型不能为空");
        int sheet = context.getSheetNo() == null ? 0 : context.getSheetNo();
        Assert.isTrue(sheet >= 0, "Sheet序号不能为负数");
        return sheet;
    }

    /**
     * 将原列表拆成有限批次，不复制整个数据集；调用方在写完前不得修改列表。
     *
     * @param rows 原数据列表
     * @param <T> 行类型
     * @return 一次性顺序批次来源
     */
    public static <T> Supplier<List<T>> batches(List<T> rows) {
        Assert.notNull(rows, "导出数据不能为null");
        return new Supplier<>() {
            /**
             * 下一批起点，仅在调用线程使用。
             */
            private int position;

            /**
             * 返回最多一个模板允许的批次，结束后为空列表。
             *
             * @return 下一批数据
             */
            @Override
            public List<T> get() {
                int end = (int) Math.min(rows.size(), (long) position + ExcelTemplate.MAX_BATCH_ROWS);
                List<T> batch = rows.subList(position, end);
                position = end;
                return batch;
            }
        };
    }
}
