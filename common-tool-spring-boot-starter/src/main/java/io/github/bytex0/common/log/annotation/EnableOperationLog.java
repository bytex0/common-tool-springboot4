package io.github.bytex0.common.log.annotation;

import io.github.bytex0.common.log.config.OperationLogAutoConfiguration;
import io.github.bytex0.common.log.service.OperationLogHandler;
import io.github.bytex0.common.log.service.OperatorInfoProvider;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/**
 * 启用操作日志，保留处理器及操作者提供者选择能力；已有用户 Bean 优先。
 *
 * @author bytex0
 * @since 2026-10-06 18:55:22
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration(OperationLogAutoConfiguration.class)
public @interface EnableOperationLog {

    /**
     * 无用户处理器 Bean 时使用的实现类型，默认标记值选择内置处理器。
     * 注解指定类需无参构造；需要依赖注入的实现应注册为普通 Spring Bean。
     *
     * @return 处理器类型
     */
    Class<? extends OperationLogHandler> handler() default OperationLogHandler.class;

    /**
     * 无用户提供者 Bean 时使用的实现类型，默认标记值选择 unknown 提供者。
     * 注解指定类需无参构造，构造失败会明确阻止启动，不静默降级。
     *
     * @return 提供者类型
     */
    Class<? extends OperatorInfoProvider> provider() default OperatorInfoProvider.class;
}
