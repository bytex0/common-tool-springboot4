package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import io.github.bytex0.desensitize.enums.DesensitizeType;

/**
 * 车牌处理器(CarNumberDesensitizeHandler)保留前两码点及最后一码点，避免短值越界。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
@DesensitizeFor(DesensitizeType.CAR_NUMBER)
public class CarNumberDesensitizeHandler extends AbstractDesensitizeHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public String desensitize(String value) {
        return DesensitizeRules.apply(value, DesensitizeType.CAR_NUMBER);
    }
}
