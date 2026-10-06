package io.github.bytex0.desensitize.fastjson;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 属性脱敏(PropertyMasker)解析继承字段、getter 和显式序列化别名，不反射读取敏感字段内容。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
final class PropertyMasker {

    /**
     * 当前上下文的实际策略。
     */
    private final DesensitizeHandlerFactory factory;

    /**
     * 随类生命周期回收的不可变规则缓存，不建立静态强引用类表。
     */
    private final ClassValue<Map<String, Rule>> rules = new ClassValue<>() {
        /**
         * 首次遇到模型时解析其注解，不缓存任何实例或敏感值。
         *
         * @param type 模型类
         * @return 属性名及别名映射
         */
        @Override
        protected Map<String, Rule> computeValue(Class<?> type) {
            return inspect(type);
        }
    };

    /**
     * 创建模型规则解析器。
     *
     * @param factory 策略工厂
     */
    PropertyMasker(DesensitizeHandlerFactory factory) {
        Assert.notNull(factory, "脱敏工厂不能为空");
        this.factory = factory;
    }

    /**
     * 处理序列化器已经读取的属性值；失败不能回退为原文。
     *
     * @param object 所属对象
     * @param name 序列化属性名
     * @param value 序列化属性值
     * @return 脱敏或未标注的原属性值
     */
    Object process(Object object, String name, Object value) {
        if (object == null) {
            return value;
        }
        Map<String, Rule> mapping = rules.get(object.getClass());
        Rule rule = mapping.get(name);
        if (rule == null) {
            Assert.isTrue(mapping.values().stream().noneMatch(candidate -> candidate.annotation() != null),
                    "无法识别脱敏模型的输出属性名，请使用明确的JSONField别名");
            return value;
        }
        if (rule.annotation() == null) {
            return value;
        }
        Assert.isTrue(rule.type() == String.class, "Desensitize仅支持String属性");
        return value == null ? null : factory.mask((String) value, rule.annotation());
    }

    /**
     * 解析继承字段和 JavaBeans getter，getter 注解优先于同属性字段。
     *
     * @param type 模型类
     * @return 不可变规则表
     */
    private Map<String, Rule> inspect(Class<?> type) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.putIfAbsent(field.getName(), field);
                }
            }
        }
        Map<String, Rule> result = new LinkedHashMap<>();
        fields.forEach((name, field) -> {
            Desensitize annotation = field.getAnnotation(Desensitize.class);
            register(result, new Rule(name, field.getType(), annotation), field);
        });
        try {
            for (PropertyDescriptor property : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                Method getter = property.getReadMethod();
                if (getter == null) {
                    continue;
                }
                Desensitize annotation = AnnotatedElementUtils.findMergedAnnotation(getter, Desensitize.class);
                Field field = fields.get(property.getName());
                if (annotation == null && field != null) {
                    annotation = field.getAnnotation(Desensitize.class);
                }
                Rule rule = new Rule(property.getName(), getter.getReturnType(), annotation);
                register(result, rule, getter);
                if (field != null) {
                    register(result, rule, field);
                }
            }
        } catch (IntrospectionException exception) {
            throw new IllegalArgumentException("无法解析脱敏属性", exception);
        }
        return Map.copyOf(result);
    }

    /**
     * 注册逻辑属性及明确的 JSONField/JsonProperty 别名，不依赖其可选类库是否加载。
     *
     * @param result 规则表
     * @param rule 当前规则
     * @param member 字段或方法
     */
    private void register(Map<String, Rule> result, Rule rule, AnnotatedElement member) {
        put(result, rule.name(), rule);
        for (Annotation annotation : member.getAnnotations()) {
            String type = annotation.annotationType().getName();
            String attribute = switch (type) {
                case "com.alibaba.fastjson.annotation.JSONField", "com.alibaba.fastjson2.annotation.JSONField" -> "name";
                case "com.fasterxml.jackson.annotation.JsonProperty" -> "value";
                default -> null;
            };
            if (attribute != null && AnnotationUtils.getValue(annotation, attribute) instanceof String alias
                    && !alias.isEmpty()) {
                put(result, alias, rule);
            }
        }
    }

    /**
     * 拒绝两个不同脱敏属性映射到同一个别名，避免规则被扫描顺序覆盖。
     *
     * @param result 规则表
     * @param name 输出属性名
     * @param rule 当前规则
     */
    private void put(Map<String, Rule> result, String name, Rule rule) {
        Rule previous = result.get(name);
        Assert.isTrue(previous == null || previous.name().equals(rule.name()), "脱敏属性别名冲突");
        result.put(name, rule);
    }

    /**
     * 属性规则(Rule)只保存类型与注解，不保存实例和值。
     *
     * @param name 逻辑名称
     * @param type 声明类型
     * @param annotation 脱敏注解
     * @author linshiqiang
     * @since 2026-10-06 10:31:46
     */
    private record Rule(
            /**
             * 逻辑属性名称。
             */
            String name,

            /**
             * 声明的属性类型。
             */
            Class<?> type,

            /**
             * 属性脱敏参数，普通属性为 null。
             */
            Desensitize annotation) {
    }
}
