package io.github.bytex0.excel.core.exporter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ZIP 导出上下文(LargeExcelZipExportContext)保留文件行上限、查询大小和临时目录。
 *
 * @param <R> 行类型
 * @param <P> 查询参数类型
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LargeExcelZipExportContext<R, P> extends ExportContext<R, P> {

    /**
     * 每个工作簿唯一 Sheet 的最大数据行数，默认 500000，范围 1 至 1000000。
     */
    private int maxRowsPerSheet = 500000;

    /**
     * ZIP 查询批次大小，默认 5000，范围 1 至 10000；覆盖继承的 pageSize。
     */
    private int fetchSize = 5000;

    /**
     * 可选临时根目录，空时使用系统临时目录；只创建和删除本次随机临时文件。
     */
    private String tempDir;

    /**
     * {@inheritDoc}
     */
    @Override
    public LargeExcelZipExportContext<R, P> clone() {
        return (LargeExcelZipExportContext<R, P>) super.clone();
    }
}
