package io.github.bytex0.desensitize.handler;

import io.github.bytex0.desensitize.annotation.Desensitize;

/**
 * 脱敏处理器(DesensitizeHandler)业务自定义策略
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@FunctionalInterface
public interface DesensitizeHandler {

    /**
     * 使用处理器默认策略脱敏；实现应保持线程安全，不修改输入对象。
     *
     * @param value 原始文本，可为 null
     * @return 脱敏文本，null 输入允许返回 null
     */
    String desensitize(String value);

    /**
     * 使用注解处理；默认实现交给简单策略，支持范围的实现应继承 AbstractDesensitizeHandler。
     *
     * @param value 原始文本
     * @param annotation 本属性的注解参数
     * @return 脱敏文本
     */
    default String desensitize(String value, Desensitize annotation) {
        return desensitize(value);
    }

    /**
     * 保留原查询条件扩展入口；默认只返回输入本身，不代表能够反向恢复原文。
     *
     * @param value 已脱敏文本，可为 null
     * @return 默认单元素数组
     */
    default String[] reverse(String value) {
        return new String[]{value};
    }
}
