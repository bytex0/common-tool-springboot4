package io.github.bytex0.dict.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字典注解(Dict)保留code并追加文本属性
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface Dict {

    /**
     * 非空字典类型，先查询加载器缓存，缺少值时才使用显式表字段回退。
     *
     * @return 类型编码
     */
    String value();

    /**
     * 追加到实际 JSON 属性名后的文本后缀，默认 Text；生成名称不能与其他属性冲突。
     *
     * @return 字段后缀
     */
    String suffix() default "Text";

    /**
     * 原数据库回退表名，默认不使用；只允许可信简单 SQL 标识符。
     *
     * @return 表名
     */
    String table() default "";

    /**
     * 原数据库回退显示列，默认不使用；只允许可信简单 SQL 标识符。
     *
     * @return 文本字段
     */
    String field() default "";

    /**
     * 数据库匹配列，默认空串表示沿用原协议以 field 匹配编码，编码值始终参数绑定。
     *
     * @return 编码列名
     */
    String codeField() default "";
}
