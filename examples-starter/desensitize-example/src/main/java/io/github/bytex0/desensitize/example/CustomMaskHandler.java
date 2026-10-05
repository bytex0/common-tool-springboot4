package io.github.bytex0.desensitize.example;

import io.github.bytex0.desensitize.handler.DesensitizeHandler;

/**
 * 自定义脱敏(CustomMaskHandler)验证通过Spring构造参数创建
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
public class CustomMaskHandler implements DesensitizeHandler {

    /**
     * 构造注入的替代内容
     */
    private final String replacement;

    public CustomMaskHandler(String replacement) { this.replacement = replacement; }

    @Override
    public String desensitize(String value) { return replacement; }
}
