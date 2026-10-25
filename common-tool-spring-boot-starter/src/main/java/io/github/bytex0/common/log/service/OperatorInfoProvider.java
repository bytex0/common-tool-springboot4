package io.github.bytex0.common.log.service;

/**
 * 当前操作者信息提供者，业务通过普通 Spring Bean 提供身份上下文。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
public interface OperatorInfoProvider {

    /**
     * 无状态默认提供者，不引用任何应用上下文。
     */
    OperatorInfoProvider DEFAULT = new DefaultOperatorInfoProvider();

    /**
     * 获取操作人标识。
     *
     * @return 标识，可为空
     */
    String getOperatorId();

    /**
     * 获取操作人名称。
     *
     * @return 名称，可为空
     */
    String getOperatorName();

    /**
     * 获取部门标识。
     *
     * @return 部门标识，可为空
     */
    String getOperatorDeptId();

    /**
     * 获取部门名称。
     *
     * @return 部门名称，可为空
     */
    String getOperatorDeptName();
}
