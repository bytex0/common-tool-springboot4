package io.github.bytex0.util;

import io.github.bytex0.exception.ParamsException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/**
 * 实例隔离的参数校验工具，由调用方或 Spring 注入 Validator，不静态获取应用 Bean。
 *
 * @author bytex0
 * @since 2026-10-06 14:54:20
 */
public class ValidationUtil {

    /**
     * 外部拥有的校验器，本工具不负责关闭其 ValidatorFactory。
     */
    private final Validator validator;

    /**
     * 创建使用指定校验器的工具。
     *
     * @param validator 非空校验器
     */
    public ValidationUtil(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * 按默认分组校验整个对象。
     *
     * @param object 非空待校验对象
     * @throws ParamsException 存在约束违规
     */
    public void validate(Object object) {
        validate(object, new Class<?>[0]);
    }

    /**
     * 按指定分组校验整个对象。
     *
     * @param object 非空待校验对象
     * @param groups 分组，为空数组使用默认组
     * @throws ParamsException 存在约束违规
     */
    public void validate(Object object, Class<?>... groups) {
        check(validator.validate(object, groups));
    }

    /**
     * 按默认分组校验指定属性。
     *
     * @param object 非空对象
     * @param propertyName 属性名称
     * @throws ParamsException 存在约束违规
     */
    public void validate(Object object, String propertyName) {
        validate(object, propertyName, new Class<?>[0]);
    }

    /**
     * 按指定分组校验属性。
     *
     * @param object 非空对象
     * @param propertyName 属性名称
     * @param groups 分组
     * @throws ParamsException 存在约束违规
     */
    public void validate(Object object, String propertyName, Class<?>... groups) {
        check(validator.validateProperty(object, propertyName, groups));
    }

    /**
     * 按属性路径和消息排序，稳定选择第一条违规，不将被校验值输出到错误消息。
     *
     * @param violations 约束违规集合
     */
    private void check(Set<ConstraintViolation<Object>> violations) {
        violations.stream().sorted(Comparator
                .comparing((ConstraintViolation<Object> violation) -> violation.getPropertyPath().toString())
                .thenComparing(ConstraintViolation::getMessage))
                .findFirst().ifPresent(violation -> {
                    throw new ParamsException(violation.getMessage());
                });
    }
}
