package io.github.bytex0.sensitive.handler;

import io.github.bytex0.sensitive.annotation.SensitiveWordCheck;
import io.github.bytex0.sensitive.annotation.SensitiveWordField;
import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.MatchType;
import io.github.bytex0.sensitive.core.SensitiveWordFilter;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.properties.SensitiveWordProperties;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原切面入口(SensitiveWordAspect)通过精确 Advisor 支持参数注解，先完成全部校验再修改对象。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public class SensitiveWordAspect implements MethodInterceptor {

    /**
     * 与程序化入口共享白名单的处理服务。
     */
    private final SensitiveWordOperations operations;

    /**
     * 保留原直接过滤器构造方式，白名单来自原配置。
     *
     * @param filter 过滤器
     * @param properties 原配置
     */
    public SensitiveWordAspect(SensitiveWordFilter filter, SensitiveWordProperties properties) {
        this(new SensitiveWordOperations(filter, properties));
        operations.replaceWhiteList(properties.getWhiteList());
    }

    /**
     * 默认注入共享业务服务。
     *
     * @param operations 业务组件
     */
    public SensitiveWordAspect(SensitiveWordOperations operations) {
        this.operations = operations;
    }

    /**
     * Advisor 调用入口，保留 Spring 代理链。
     *
     * @param invocation 当前调用
     * @return 业务结果
     * @throws Throwable 原业务或校验异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = Objects.requireNonNull(invocation.getThis());
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), target.getClass());
        Object[] processed = process(method, invocation.getMethod(), invocation.getArguments());
        System.arraycopy(processed, 0, invocation.getArguments(), 0, processed.length);
        return invocation.proceed();
    }

    /**
     * 保留原手工 around 入口，自动配置不重复注册第二套切面。
     *
     * @param point 连接点
     * @return 业务结果
     * @throws Throwable 原异常
     */
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method declared = ((MethodSignature) point.getSignature()).getMethod();
        Method method = AopUtils.getMostSpecificMethod(declared, point.getTarget().getClass());
        return point.proceed(process(method, declared, point.getArgs()));
    }

    /**
     * 只匹配明确标记的方法或参数；字段策略只在显式检查边界生效，避免控制器与服务隐式重复处理。
     *
     * @param method 调用方法
     * @param targetClass 目标类型
     * @return 是否需要代理
     */
    public static boolean matches(Method method, Class<?> targetClass) {
        Method actual = AopUtils.getMostSpecificMethod(method, targetClass);
        if (check(actual) != null || check(method) != null) {
            return true;
        }
        for (int index = 0; index < actual.getParameterCount(); index++) {
            if (actual.getParameters()[index].isAnnotationPresent(SensitiveWordCheck.class)
                    || method.getParameters()[index].isAnnotationPresent(SensitiveWordCheck.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算全部变更；任何一个参数拒绝时，都不对其他对象留下部分修改。
     *
     * @param actual 目标方法
     * @param declared 代理方法
     * @param arguments 原参数
     * @return 处理后的参数副本
     */
    private Object[] process(Method actual, Method declared, Object[] arguments) {
        Object[] result = arguments.clone();
        List<Runnable> changes = new ArrayList<>();
        SensitiveWordCheck methodRule = check(actual) == null ? check(declared) : check(actual);
        for (int index = 0; index < result.length; index++) {
            SensitiveWordCheck parameter = actual.getParameters()[index].getAnnotation(SensitiveWordCheck.class);
            if (parameter == null) {
                parameter = declared.getParameters()[index].getAnnotation(SensitiveWordCheck.class);
            }
            SensitiveWordCheck rule = parameter == null ? methodRule : parameter;
            if (rule == null) {
                continue;
            }
            if (result[index] instanceof String text && rule != null) {
                result[index] = operations.process(text, rule.handleType(), rule.matchType(), rule.replaceChar(), rule.message());
            } else if (result[index] != null && !(result[index] instanceof String)) {
                objectChanges(result[index], rule, changes);
            }
        }
        changes.forEach(Runnable::run);
        return result;
    }

    /**
     * 处理直接字符串字段，字段策略优先；拒绝修改 final 字段，不无声跳过不可变模型。
     *
     * @param object 对象
     * @param rule 方法或参数规则，可为空
     * @param changes 待应用修改
     */
    private void objectChanges(Object object, SensitiveWordCheck rule, List<Runnable> changes) {
        List<Field> fields = fields(object.getClass());
        Set<String> selected = rule == null ? Set.of() : new HashSet<>(Arrays.asList(rule.fields()));
        if (!selected.isEmpty()) {
            Set<String> available = new HashSet<>();
            fields.forEach(field -> available.add(field.getName()));
            Assert.isTrue(available.containsAll(selected), "检测字段不存在或不是可检测字符串字段");
        }
        for (Field field : fields) {
            if (!selected.isEmpty() && !selected.contains(field.getName())) {
                continue;
            }
            SensitiveWordField specific = field.getAnnotation(SensitiveWordField.class);
            if (specific == null && rule == null) {
                continue;
            }
            ReflectionUtils.makeAccessible(field);
            String original = (String) ReflectionUtils.getField(field, object);
            HandleType handling = specific == null ? rule.handleType() : specific.handleType();
            MatchType matching = specific == null ? rule.matchType() : specific.matchType();
            char replacement = specific == null ? rule.replaceChar() : specific.replaceChar();
            String processed = operations.process(original, handling, matching, replacement,
                    (rule == null ? "内容包含敏感词" : rule.message()) + " [字段: " + field.getName() + "]");
            if (!Objects.equals(original, processed)) {
                Assert.isTrue(!Modifier.isFinal(field.getModifiers()), "不可变字段不能原地改写，请使用程序化接口返回新值");
                changes.add(() -> ReflectionUtils.setField(field, object, processed));
            }
        }
    }

    /**
     * 读取方法及组合注解。
     *
     * @param method 方法
     * @return 规则或 null
     */
    private static SensitiveWordCheck check(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, SensitiveWordCheck.class);
    }

    /**
     * 获取对象及父类的直接实例字符串字段，不展开 JDK 类型、集合或嵌套对象。
     *
     * @param type 参数类型
     * @return 字段列表
     */
    private List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (current.getPackageName().startsWith("java.") || current.isArray() || current.isPrimitive()) {
                break;
            }
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    result.add(field);
                }
            }
        }
        return result;
    }
}
