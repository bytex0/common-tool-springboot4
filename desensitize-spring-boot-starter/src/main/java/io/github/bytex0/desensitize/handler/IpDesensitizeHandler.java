package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.DesensitizeFor;
import io.github.bytex0.desensitize.enums.DesensitizeType;

/**
 * IPv4 处理器(IpDesensitizeHandler)保留首末段和可选端口，不发起 DNS 请求。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
@DesensitizeFor(DesensitizeType.IPV4)
public class IpDesensitizeHandler extends AbstractDesensitizeHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public String desensitize(String value) {
        return DesensitizeRules.apply(value, DesensitizeType.IPV4);
    }
}
