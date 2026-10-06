package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import io.github.bytex0.desensitize.enums.DesensitizeType;

/**
 * 密码处理器(PasswordDesensitizeHandler)固定六星，不通过输出暴露密码长度。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
@DesensitizeFor(DesensitizeType.PASSWORD)
public class PasswordDesensitizeHandler extends AbstractDesensitizeHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public String desensitize(String value) {
        return DesensitizeRules.apply(value, DesensitizeType.PASSWORD);
    }
}
