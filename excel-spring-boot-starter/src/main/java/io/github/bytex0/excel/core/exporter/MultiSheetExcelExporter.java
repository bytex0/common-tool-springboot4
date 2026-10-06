package io.github.bytex0.excel.core.exporter;

import io.github.bytex0.excel.ExcelTemplate;
import io.github.bytex0.excel.core.ExcelSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 多 Sheet 导出器(MultiSheetExcelExporter)保留原查询回调，以有界预取保证页序和精确拆分。
 *
 * @param <R> 行类型
 * @param <P> 查询参数类型
 * @author linshiqiang
 * @since 2026-10-06 11:06:59
 */
public abstract class MultiSheetExcelExporter<R, P> {

    /**
     * 调用方管理的查询执行器，不由导出操作关闭。
     */
    protected final ExecutorService executorService;

    /**
     * 接收共享命名执行器。
     *
     * @param executorService 查询执行器
     */
    protected MultiSheetExcelExporter(@Qualifier("excelThreadPool") ExecutorService executorService) {
        Assert.notNull(executorService, "Excel执行器不能为空");
        this.executorService = executorService;
    }

    /**
     * 获取指定一基页的数据，需与总数使用一致查询条件和稳定排序。
     *
     * @param context 单页上下文浅副本
     * @return 非空列表，除最后一页外大小必须等于 pageSize
     */
    public abstract List<R> getExportData(MultiSheetExportContext<R, P> context);

    /**
     * 获取一致查询快照的总行数。
     *
     * @param context 上下文浅副本
     * @return 非空非负总数
     */
    public abstract Long getTotalCount(MultiSheetExportContext<R, P> context);

    /**
     * 预取和写出流水处理；空数据仍输出合法工作簿，错误会取消实际在途任务。
     *
     * @param context 导出上下文
     * @param response HTTP 响应，输出流由容器拥有
     * @throws Exception 获取响应流或导出失败时抛出
     */
    public void exportMultiSheetExcel(MultiSheetExportContext<R, P> context,
                                      HttpServletResponse response) throws Exception {
        Assert.notNull(context, "导出上下文不能为空");
        MultiSheetExportContext<R, P> snapshot = context.clone();
        Assert.notNull(snapshot.getEntityClass(), "行类型不能为空");
        Assert.notNull(snapshot.getPageSize(), "分页大小不能为空");
        Assert.notNull(snapshot.getMaxRowsPerSheet(), "Sheet行数不能为空");
        Assert.isTrue(snapshot.getMaxRowsPerSheet() > 0
                && snapshot.getMaxRowsPerSheet() <= ExcelTemplate.MAX_SHEET_ROWS, "Sheet行数越界");
        ExcelSupport.fileName(snapshot.getFileName());
        Long total = getTotalCount(snapshot.clone());
        Assert.notNull(total, "总行数不能为null");
        try (OrderedPageSource<R> source = new OrderedPageSource<>(total, snapshot.getPageSize(), executorService,
                page -> {
                    MultiSheetExportContext<R, P> request = snapshot.clone();
                    request.setCurrentPage(page);
                    return getExportData(request);
                }, snapshot.getOperationTimeout(), snapshot.getCancellationTimeout())) {
            ExcelSupport.prepare(response, snapshot.getFileName(), false);
            new ExcelTemplate().writeNamed(response.getOutputStream(), snapshot.getEntityClass(),
                    index -> ExcelSupport.sheetName(snapshot.getSheetName(), index, true),
                    source, snapshot.getMaxRowsPerSheet());
        }
    }
}
