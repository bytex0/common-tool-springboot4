package io.github.bytex0.excel.core.exporter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多 Sheet 上下文(MultiSheetExportContext)保留分页和数据行上限。
 *
 * @param <R> 行类型
 * @param <P> 查询参数类型
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MultiSheetExportContext<R, P> extends ExportContext<R, P> {

    /**
     * 每个 Sheet 的最大数据行数，默认 500000，范围 1 至 1000000，不包含表头。
     */
    private Integer maxRowsPerSheet = 500000;

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiSheetExportContext<R, P> clone() {
        return (MultiSheetExportContext<R, P>) super.clone();
    }
}
