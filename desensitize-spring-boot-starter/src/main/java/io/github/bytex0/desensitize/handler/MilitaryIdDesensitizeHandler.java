package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import io.github.bytex0.desensitize.enums.DesensitizeType;

/**
 * 军官证处理器(MilitaryIdDesensitizeHandler)保留前后两码点，短值全部遮蔽。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
@DesensitizeFor(DesensitizeType.MILITARY_ID)
public class MilitaryIdDesensitizeHandler extends AbstractDesensitizeHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public String desensitize(String value) {
        return DesensitizeRules.apply(value, DesensitizeType.MILITARY_ID);
    }
}
