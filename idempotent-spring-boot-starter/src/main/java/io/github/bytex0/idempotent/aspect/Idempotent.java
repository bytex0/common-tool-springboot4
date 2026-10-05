package io.github.bytex0.idempotent.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解(Idempotent)适用于通过Spring代理调用的服务方法
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Idempotent {

    /**
     * 保留原默认命名空间；显式空串表示采用 idempotent.key-prefix 配置。
     *
     * @return 业务键前缀
     */
    String keyPrefix() default "idempotent:";

    /**
     * 可信 SpEL，支持参数名、#p0/#a0 和 Bean 引用；默认空串采用规范化参数摘要。
     *
     * @return 业务键表达式
     */
    String key() default "";

    /**
     * 成功后的去重秒数，保留原默认 10 秒；不大于零时使用配置默认值。
     *
     * @return 去重时长，单位为秒
     */
    long expire() default 10;
}
