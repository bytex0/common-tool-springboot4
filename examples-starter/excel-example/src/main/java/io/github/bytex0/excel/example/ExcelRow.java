package io.github.bytex0.excel.example;

import lombok.Getter;
import lombok.Setter;
import org.apache.fesod.sheet.annotation.ExcelProperty;

/**
 * Excel行(ExcelRow)示例数据模型
 *
 * @author bytex0
 * @since 2026-10-05 15:36:55
 */
@Getter
@Setter
public class ExcelRow {

    /**
     * 行编号
     */
    @ExcelProperty("编号")
    private Integer id;

    /**
     * 中文名称
     */
    @ExcelProperty("名称")
    private String name;
}
