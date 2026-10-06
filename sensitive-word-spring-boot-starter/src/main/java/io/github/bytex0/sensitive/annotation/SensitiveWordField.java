package io.github.bytex0.sensitive.annotation;

import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.MatchType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段策略(SensitiveWordField)在方法或参数 SensitiveWordCheck 触发的边界内覆盖默认策略。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveWordField {

    /**
     * 字段默认最小匹配。
     *
     * @return 匹配模式
     */
    MatchType matchType() default MatchType.MIN_MATCH;

    /**
     * 字段默认替换。
     *
     * @return 处理模式
     */
    HandleType handleType() default HandleType.REPLACE;

    /**
     * 默认替换星号。
     *
     * @return 替换字符
     */
    char replaceChar() default '*';
}
