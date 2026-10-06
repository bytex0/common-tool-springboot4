package io.github.bytex0.sensitive.annotation;

import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.MatchType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 入站检测(SensitiveWordCheck)支持方法和独立参数标记，参数策略优先于方法策略。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@Documented
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveWordCheck {

    /**
     * 对象参数的字符串字段名，空表示全部；未知或非字符串字段明确报错。
     *
     * @return 字段名
     */
    String[] fields() default {};

    /**
     * 默认最小匹配，字段注解可覆盖。
     *
     * @return 策略
     */
    MatchType matchType() default MatchType.MIN_MATCH;

    /**
     * 默认拒绝，字段注解可覆盖。
     *
     * @return 处理方式
     */
    HandleType handleType() default HandleType.EXCEPTION;

    /**
     * 替换模式使用的字符，默认星号。
     *
     * @return 替换字符
     */
    char replaceChar() default '*';

    /**
     * 拒绝消息，默认不包含原文。
     *
     * @return 消息
     */
    String message() default "内容包含敏感词";
}
