package io.github.bytex0.common.model;

import lombok.Data;

/**
 * 通用审计字段模型，未提供的审计信息保持 null，由业务层赋值。
 *
 * @author bytex0
 * @since 2026-10-06 14:39:06
 */
@Data
public class BaseDTO {

    /**
     * 更新时间戳，单位毫秒，可为空。
     */
    private Long updateTime;

    /**
     * 创建时间戳，单位毫秒，可为空。
     */
    private Long createTime;

    /**
     * 创建人标识，可为空。
     */
    private String createBy;

    /**
     * 更新人标识，可为空。
     */
    private String updateBy;
}
