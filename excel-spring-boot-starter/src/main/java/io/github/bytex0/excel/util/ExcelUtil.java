package io.github.bytex0.excel.util;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.ExcelSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.fesod.sheet.read.listener.PageReadListener;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Excel 工具(ExcelUtil)保留原静态无状态导入导出入口，资源所有权明确。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
public final class ExcelUtil {

    /**
     * 无状态工具不允许实例化。
     */
    private ExcelUtil() {
    }

    /**
     * 写入响应流，保留原文件名和首 Sheet 名，超行时自动拆分。
     *
     * @param data 数据列表，写完前不得修改
     * @param fileName 文件基础名
     * @param response HTTP 响应
     * @param clazz 行类型
     * @param <R> 行类型
     * @throws IOException 获取响应输出失败时抛出
     */
    public static <R> void exportToExcel(List<R> data, String fileName, HttpServletResponse response,
                                         Class<R> clazz) throws IOException {
        ExcelSupport.prepare(response, fileName, false);
        new ExcelTemplate().writeNamed(response.getOutputStream(), clazz,
                index -> ExcelSupport.sheetName(fileName, index, false), ExcelSupport.batches(data),
                ExcelTemplate.MAX_SHEET_ROWS);
    }

    /**
     * 使用调用方监听器导入第一个 Sheet，关闭本次上传输入流。
     *
     * @param file 上传文件
     * @param clazz 行类型
     * @param listener Apache Fesod 批次监听器，代替旧 SDK 类型
     * @param <R> 行类型
     * @throws IOException 打开上传流失败时抛出
     */
    public static <R> void importFromExcel(MultipartFile file, Class<R> clazz,
                                           PageReadListener<R> listener) throws IOException {
        Assert.notNull(file, "上传文件不能为空");
        try (InputStream input = file.getInputStream()) {
            new ExcelTemplate().readWithListener(input, clazz, 0, listener);
        }
    }
}
