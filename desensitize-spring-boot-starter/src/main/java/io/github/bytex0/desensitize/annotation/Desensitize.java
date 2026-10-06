package io.github.bytex0.desensitize.annotation;

import io.github.bytex0.desensitize.enums.DesensitizeType;
import io.github.bytex0.desensitize.handler.DesensitizeHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏(Desensitize)出站JSON规则
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Documented
public @interface Desensitize {

    /**
     * 默认策略类型，必填；显式范围覆盖内置策略，CUSTOM 由指定处理器决定。
     *
     * @return 脱敏策略
     */
    DesensitizeType type();

    /**
     * CUSTOM 使用的处理器类型，优先查找 Spring Bean，否则使用公开无参构造器。
     * 默认接口类型不能用于 CUSTOM，创建或处理失败明确抛出，不返回原文。
     *
     * @return 处理器类
     */
    Class<? extends DesensitizeHandler> handler() default DesensitizeHandler.class;

    /**
     * 覆盖范围的起点，按 Unicode 码点计数，包含起点，默认 0；负数从末尾倒数。
     * 非默认范围或替换字符使内置处理器使用显式范围，索引会截断到有效区间。
     *
     * @return 起点
     */
    int startIndex() default 0;

    /**
     * 覆盖范围的终点，不包含终点；默认 -1 特指整个字符串末尾，其他负数从末尾倒数。
     * 截断后的起点不得大于等于终点，避免无效配置静默返回原文。
     *
     * @return 终点
     */
    int endIndex() default -1;

    /**
     * 每个被遮蔽码点使用的替换文本，默认星号，长度为 1 至 8 个 Unicode 码点。
     * 与原抽象处理器一致，单独修改此项也启用显式范围；默认范围为整串。
     *
     * @return 替换文本
     */
    String maskChar() default "*";
}
