package io.github.bytex0.desensitize.jackson;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import org.springframework.util.Assert;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.List;

/**
 * Jackson脱敏(DesensitizeModule)仅为显式标记的String属性分配独立序列化器
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
public class DesensitizeModule extends SimpleModule {

    public DesensitizeModule(DesensitizeHandlerFactory factory) {
        super("CommonToolDesensitize");
        setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier bean,
                                                             List<BeanPropertyWriter> properties) {
                for (BeanPropertyWriter property : properties) {
                    Desensitize annotation = property.getAnnotation(Desensitize.class);
                    if (annotation != null) {
                        Assert.isTrue(property.getType().hasRawClass(String.class), "Desensitize仅支持String属性");
                        property.assignSerializer(new ValueSerializer<>() {
                            @Override
                            public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
                                generator.writeString(factory.mask((String) value, annotation));
                            }
                        });
                    }
                }
                return properties;
            }
        });
    }
}
