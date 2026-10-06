package io.github.bytex0.config;

import io.github.bytex0.util.ValidationUtil;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 参数校验工具条件装配，仅使用当前应用已有的 Validator，不隐式创建校验工厂。
 *
 * @author bytex0
 * @since 2026-10-06 14:54:20
 */
@AutoConfiguration(afterName = "org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration")
@ConditionalOnClass(Validator.class)
@ConditionalOnProperty(prefix = "common-tool", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CommonValidationAutoConfiguration {

    /**
     * 创建实例校验工具，允许用户覆盖。
     *
     * @param validator 当前应用校验器
     * @return 校验工具
     */
    @Bean
    @ConditionalOnBean(Validator.class)
    @ConditionalOnMissingBean
    public ValidationUtil validationUtil(Validator validator) {
        return new ValidationUtil(validator);
    }
}
