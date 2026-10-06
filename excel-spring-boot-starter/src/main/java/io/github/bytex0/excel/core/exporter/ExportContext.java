package io.github.bytex0.excel.core.exporter;

import lombok.Data;
import java.time.Duration;

/**
 * 导出上下文(ExportContext)保存查询和文件信息，clone 保持原来的浅复制语义。
 *
 * @param <R> 导出行类型
 * @param <P> 查询参数类型
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
@Data
public class ExportContext<R, P> implements Cloneable {

    /**
     * 可选业务任务标识，不作为本地目录或文件路径使用。
     */
    private String uniqueId;

    /**
     * 下载文件基础名称，必填；响应头和 ZIP 条目会清理路径及控制字符。
     */
    private String fileName;

    /**
     * Sheet 基础名称，可为空，默认使用 Sheet；写入前进行 Excel 名称规范化。
     */
    private String sheetName;

    /**
     * 查询分页大小，默认 1000，必须为 1 至 10000；不要求整除每 Sheet 行数。
     */
    private Integer pageSize = 1000;

    /**
     * 一基页码，默认 1；普通单次导出原样传递，分页导出使用局部副本推进。
     */
    private Integer currentPage = 1;

    /**
     * 业务查询参数，允许 null；浅复制后仍共享该引用，导出过程中不得并发修改。
     */
    private P queryParams;

    /**
     * 导出行类型，必填，使用 Apache Fesod 的模型注解。
     */
    private Class<R> entityClass;

    /**
     * 分页预取和等待的协作式预算，默认 5 分钟，必须为正；业务查询还需配置自身的 I/O 超时。
     */
    private Duration operationTimeout = Duration.ofMinutes(5);

    /**
     * 取消预取后等待实际查询退出的上限，默认 10 秒，必须为正。
     */
    private Duration cancellationTimeout = Duration.ofSeconds(10);

    /**
     * 复制当前运行时类型的所有字段，保持查询参数的浅引用，不修改原上下文。
     *
     * @return 相同类型的浅副本
     */
    @Override
    @SuppressWarnings("unchecked")
    public ExportContext<R, P> clone() {
        try {
            return (ExportContext<R, P>) super.clone();
        } catch (CloneNotSupportedException exception) {
            throw new IllegalStateException("导出上下文必须支持复制", exception);
        }
    }
}
