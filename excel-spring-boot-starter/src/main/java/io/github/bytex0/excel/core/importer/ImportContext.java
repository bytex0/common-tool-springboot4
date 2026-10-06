package io.github.bytex0.excel.core.importer;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 导入上下文(ImportContext)描述上传文件、目标 Sheet 和模型，操作完成前不得并发修改。
 *
 * @param <R> 行类型
 * @author linshiqiang
 * @since 2026-10-06 10:57:29
 */
@Data
public class ImportContext<R> {

    /**
     * 待导入文件，必填；处理器负责关闭其本次打开的输入流，不删除调用方文件。
     */
    private MultipartFile file;

    /**
     * 零基 Sheet 序号，默认 null 表示第一个 Sheet，不得为负数。
     */
    private Integer sheetNo;

    /**
     * 普通导入的消费批次大小，默认 1000，范围 1 至 10000。
     */
    private Integer batchCount = 1000;

    /**
     * 导入行类型，必填，使用 Apache Fesod 模型注解。
     */
    private Class<R> entityClass;
}
