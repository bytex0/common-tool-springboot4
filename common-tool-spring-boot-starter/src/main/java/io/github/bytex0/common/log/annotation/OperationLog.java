package io.github.bytex0.common.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法操作日志声明，请求及响应经过有界序列化与敏感字段过滤后才交给处理器。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作类型，默认 OTHER，不使用 ordinal 作为业务编码。
     *
     * @return 操作类型
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 固定操作描述，默认空文本，不作为 SpEL 执行。
     *
     * @return 描述
     */
    String description() default "";

    /**
     * 是否采集方法参数及请求体，默认 true；仍受大小限制和敏感字段过滤约束。
     *
     * @return 是否采集请求数据
     */
    boolean saveRequestData() default true;

    /**
     * 是否采集返回值，默认 true；仍受大小限制和敏感字段过滤约束。
     *
     * @return 是否采集响应数据
     */
    boolean saveResponseData() default true;
}
