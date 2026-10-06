package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import io.github.bytex0.desensitize.enums.DesensitizeType;

/**
 * 身份证处理器(IdCardDesensitizeHandler)保留首末四位并隐藏中间码点。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
@DesensitizeFor(DesensitizeType.ID_CARD)
public class IdCardDesensitizeHandler extends AbstractDesensitizeHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public String desensitize(String value) {
        return DesensitizeRules.apply(value, DesensitizeType.ID_CARD);
    }
}
