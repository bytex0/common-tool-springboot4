package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.Desensitize;
import org.springframework.util.Assert;

/**
 * 范围脱敏(AbstractDesensitizeHandler)共享 Unicode 范围和注解覆盖契约，不保存属性可变状态。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:25:51
 */
public abstract class AbstractDesensitizeHandler implements DesensitizeHandler {

    /**
     * 按 Unicode 码点范围替换，-1 终点包含到末尾，其他负数从末尾倒数。
     *
     * @param value 原始文本，null/空串原样返回
     * @param startIndex 起点，包含
     * @param endIndex 终点，不包含，-1 特指字符串末尾
     * @param maskChar 每个码点的替换文本
     * @return 脱敏文本
     */
    public String doDesensitize(String value, int startIndex, int endIndex, String maskChar) {
        return DesensitizeRules.range(value, startIndex, endIndex, maskChar);
    }

    /**
     * 非默认索引或替换文本启用范围覆盖；默认参数使用各独立处理器的原策略。
     *
     * @param value 原始文本
     * @param annotation 属性注解
     * @return 脱敏文本
     */
    @Override
    public String desensitize(String value, Desensitize annotation) {
        Assert.notNull(annotation, "脱敏注解不能为空");
        if (annotation.startIndex() != 0 || annotation.endIndex() != -1 || !"*".equals(annotation.maskChar())) {
            return doDesensitize(value, annotation.startIndex(), annotation.endIndex(), annotation.maskChar());
        }
        return desensitize(value);
    }
}
