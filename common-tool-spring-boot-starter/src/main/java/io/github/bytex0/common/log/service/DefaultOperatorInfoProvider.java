package io.github.bytex0.common.log.service;

/**
 * 不持有用户身份的默认提供者。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
public final class DefaultOperatorInfoProvider implements OperatorInfoProvider {

    /**
     * 未知身份的兼容文本。
     */
    private static final String UNKNOWN = "unknown";

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOperatorId() {
        return UNKNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOperatorName() {
        return UNKNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOperatorDeptId() {
        return UNKNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOperatorDeptName() {
        return UNKNOWN;
    }
}
