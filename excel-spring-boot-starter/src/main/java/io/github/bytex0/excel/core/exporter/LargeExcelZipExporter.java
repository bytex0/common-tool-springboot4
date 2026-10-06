package io.github.bytex0.excel.core.exporter;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.ExcelSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * ZIP 导出器(LargeExcelZipExporter)保留原分页、文件上限和临时目录，失败清理本次工作簿。
 *
 * @param <R> 行类型
 * @param <P> 查询参数类型
 * @author linshiqiang
 * @since 2026-10-06 11:06:59
 */
public abstract class LargeExcelZipExporter<R, P> {

    /**
     * 外部管理的异步查询执行器。
     */
    protected final ExecutorService executorService;

    /**
     * 接收命名执行器，不转移其生命周期。
     *
     * @param executorService 查询执行器
     */
    public LargeExcelZipExporter(@Qualifier("excelThreadPool") ExecutorService executorService) {
        Assert.notNull(executorService, "Excel执行器不能为空");
        this.executorService = executorService;
    }

    /**
     * 获取一基页，pageSize 已设置为 fetchSize。
     *
     * @param context 单页上下文浅副本
     * @return 稳定排序的非空列表
     */
    public abstract List<R> getExportData(LargeExcelZipExportContext<R, P> context);

    /**
     * 获取同一查询快照的总行数。
     *
     * @param context 上下文浅副本
     * @return 非空非负总数
     */
    public abstract Long getTotalCount(LargeExcelZipExportContext<R, P> context);

    /**
     * 导出一份 ZIP，生成时只保留一个临时工作簿，不会用 uniqueId 拼接目录。
     *
     * @param context 导出上下文
     * @param response HTTP 响应
     * @throws Exception 查询、临时文件或响应写出失败时抛出
     */
    public void exportLargeExcelToZip(LargeExcelZipExportContext<R, P> context,
                                      HttpServletResponse response) throws Exception {
        Assert.notNull(context, "导出上下文不能为空");
        LargeExcelZipExportContext<R, P> snapshot = context.clone();
        Assert.notNull(snapshot.getEntityClass(), "行类型不能为空");
        Assert.isTrue(snapshot.getMaxRowsPerSheet() > 0
                && snapshot.getMaxRowsPerSheet() <= ExcelTemplate.MAX_SHEET_ROWS, "每文件行数越界");
        String base = ExcelSupport.fileName(snapshot.getFileName());
        Path directory = snapshot.getTempDir() == null || snapshot.getTempDir().isBlank()
                ? null : Path.of(snapshot.getTempDir());
        Long total = getTotalCount(snapshot.clone());
        Assert.notNull(total, "总行数不能为null");
        try (OrderedPageSource<R> source = new OrderedPageSource<>(total, snapshot.getFetchSize(), executorService,
                page -> {
                    LargeExcelZipExportContext<R, P> request = snapshot.clone();
                    request.setCurrentPage(page);
                    request.setPageSize(snapshot.getFetchSize());
                    return getExportData(request);
                }, snapshot.getOperationTimeout(), snapshot.getCancellationTimeout())) {
            ExcelSupport.prepare(response, base, true);
            new ExcelTemplate().writeZipNamed(response.getOutputStream(), snapshot.getEntityClass(), source,
                    snapshot.getMaxRowsPerSheet(), directory, index -> base + "_" + index + ".xlsx",
                    index -> ExcelSupport.sheetName(snapshot.getSheetName(), index, true));
        }
    }
}
