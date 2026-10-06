package io.github.bytex0.desensitize.jackson;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import org.springframework.util.Assert;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * 属性序列化器(DesensitizeSerializer)以不可变上下文保存单属性注解，保留原公开构造入口。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
public class DesensitizeSerializer extends ValueSerializer<Object> {

    /**
     * 当前容器中的策略工厂。
     */
    private final DesensitizeHandlerFactory factory;

    /**
     * 本属性注解，未标记上下文为 null。
     */
    private final Desensitize annotation;

    /**
     * 创建可被 Jackson 上下文化的序列化器，根字符串不会被全局污染。
     *
     * @param factory 策略工厂
     */
    public DesensitizeSerializer(DesensitizeHandlerFactory factory) {
        this(factory, null);
    }

    /**
     * 创建一个独立的属性规则，不复用可变 serializer 状态。
     *
     * @param factory 策略工厂
     * @param annotation 本属性参数
     */
    DesensitizeSerializer(DesensitizeHandlerFactory factory, Desensitize annotation) {
        Assert.notNull(factory, "脱敏工厂不能为空");
        this.factory = factory;
        this.annotation = annotation;
    }

    /**
     * 序列化当前 String 值，处理器失败向上抛出，不降级写出原文。
     *
     * @param value String 值
     * @param generator JSON 输出器
     * @param context 当前序列化上下文
     */
    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
        Assert.isTrue(value == null || value instanceof String, "Desensitize仅支持String属性");
        String text = (String) value;
        generator.writeString(annotation == null ? text : factory.mask(text, annotation));
    }

    /**
     * 根据当前属性返回独立实例；无注解属性不能继承其他字段的规则。
     *
     * @param context 序列化上下文
     * @param property 当前属性，根值时可为 null
     * @return 独立规则序列化器
     */
    @Override
    public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
        Desensitize current = property == null ? null : property.getAnnotation(Desensitize.class);
        return new DesensitizeSerializer(factory, current);
    }
}
