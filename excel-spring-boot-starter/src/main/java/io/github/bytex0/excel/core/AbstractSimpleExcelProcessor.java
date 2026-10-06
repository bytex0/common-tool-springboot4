package io.github.bytex0.excel.core;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.exporter.ExportContext;
import io.github.bytex0.excel.core.importer.ImportContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 普通 Excel 处理器(AbstractSimpleExcelProcessor)保留原业务回调，正确使用响应流和指定 Sheet。
 *
 * @param <R> 行类型
 * @param <P> 查询参数类型
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
public abstract class AbstractSimpleExcelProcessor<R, P> {

    /**
     * 调用方或容器管理的共享执行器，普通同步路径不会关闭它。
     */
    protected final ExecutorService executorService;

    /**
     * 接收原命名执行器，资源所有权不转移。
     *
     * @param executorService 共享执行器
     */
    protected AbstractSimpleExcelProcessor(@Qualifier("excelThreadPool") ExecutorService executorService) {
        Assert.notNull(executorService, "Excel执行器不能为空");
        this.executorService = executorService;
    }

    /**
     * 处理普通导入的一个只读批次，异常立即停止。
     * 保留原上下文原始类型签名，消费方无需重写旧 override。
     *
     * @param dataList 数据批次
     * @param context 导入上下文
     */
    public abstract void handleImportData(List<R> dataList, ImportContext context);

    /**
     * 查询普通导出数据，原单次查询语义保持不变。
     *
     * @param context 查询上下文的浅副本
     * @return 非空数据列表
     */
    public abstract List<R> getExportData(ExportContext<R, P> context);

    /**
     * 读取选定 Sheet 并按 batchCount 调用业务方法，关闭本次打开的上传流。
     *
     * @param context 导入参数
     * @throws IOException 打开上传流失败时抛出
     */
    public void importExcel(ImportContext<R> context) throws IOException {
        int sheet = ExcelSupport.validateImport(context);
        Assert.notNull(context.getBatchCount(), "导入批次大小不能为空");
        try (InputStream input = context.getFile().getInputStream()) {
            new ExcelTemplate().read(input, context.getEntityClass(), sheet, context.getBatchCount(),
                    rows -> handleImportData(rows, context), false);
        }
    }

    /**
     * 导出到响应流，空数据仍返回合法表头工作簿，超大列表自动分 Sheet。
     *
     * @param context 导出参数，不修改原实例
     * @param response HTTP 响应
     * @throws IOException 获取响应流失败时抛出
     */
    public void exportExcel(ExportContext<R, P> context, HttpServletResponse response) throws IOException {
        Assert.notNull(context, "导出上下文不能为空");
        Assert.notNull(context.getEntityClass(), "导出类型不能为空");
        List<R> data = getExportData(context.clone());
        ExcelSupport.prepare(response, context.getFileName(), false);
        String sheet = context.getSheetName() == null ? context.getFileName() : context.getSheetName();
        new ExcelTemplate().writeNamed(response.getOutputStream(), context.getEntityClass(),
                index -> ExcelSupport.sheetName(sheet, index, false), ExcelSupport.batches(data),
                ExcelTemplate.MAX_SHEET_ROWS);
    }
}
