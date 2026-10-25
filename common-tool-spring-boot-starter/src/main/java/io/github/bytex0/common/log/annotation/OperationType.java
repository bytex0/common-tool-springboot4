package io.github.bytex0.common.log.annotation;

/**
 * 操作日志业务类型，保留原枚举名称，不将序号作为持久化协议。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
public enum OperationType {

    /**
     * 查询数据。
     */
    QUERY,

    /**
     * 创建数据。
     */
    CREATE,

    /**
     * 更新数据。
     */
    UPDATE,

    /**
     * 删除数据。
     */
    DELETE,

    /**
     * 导出数据。
     */
    EXPORT,

    /**
     * 导入数据。
     */
    IMPORT,

    /**
     * 其他操作。
     */
    OTHER
}
