package io.github.bytex0.desensitize.jackson;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import org.springframework.util.Assert;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.List;

/**
 * Jackson脱敏(DesensitizeModule)仅为显式标记的String属性分配独立序列化器
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
public class DesensitizeModule extends SimpleModule {

    /**
     * 原包装构造器使用的委托模块，默认属性模块时为 null。
     */
    private final JacksonModule delegate;

    /**
     * 为标注的 String 属性分配独立序列化器。
     *
     * @param factory 策略工厂
     */
    public DesensitizeModule(DesensitizeHandlerFactory factory) {
        super("CommonToolDesensitize");
        this.delegate = null;
        setSerializerModifier(new ValueSerializerModifier() {
            /**
             * 保留未标注属性，仅为合法 String 属性设置脱敏规则。
             *
             * @param config Jackson 配置
             * @param bean 模型描述
             * @param properties 已解析属性
             * @return 原属性列表
             */
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier bean,
                                                             List<BeanPropertyWriter> properties) {
                for (BeanPropertyWriter property : properties) {
                    Desensitize annotation = property.getAnnotation(Desensitize.class);
                    if (annotation != null) {
                        Assert.isTrue(property.getType().hasRawClass(String.class), "Desensitize仅支持String属性");
                        property.assignSerializer(new DesensitizeSerializer(factory, annotation));
                    }
                }
                return properties;
            }
        });
    }

    /**
     * 保留原模块包装入口，委托类型替换为 Jackson 3 的 JacksonModule。
     *
     * @param module 委托模块
     */
    public DesensitizeModule(JacksonModule module) {
        super("CommonToolDesensitize");
        Assert.notNull(module, "委托模块不能为空");
        this.delegate = module;
    }

    /**
     * 注册属性规则或原构造器传入的委托模块。
     *
     * @param context 模块注册上下文
     */
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        if (delegate != null) {
            delegate.setupModule(context);
        }
    }
}
