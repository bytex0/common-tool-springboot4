package io.github.bytex0.dict.jackson;

import io.github.bytex0.dict.DictCache;
import io.github.bytex0.dict.annotation.Dict;
import org.springframework.util.Assert;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字典JSON(DictModule)尊重属性名称、包含规则和已有序列化器
 *
 * @author linshiqiang
 * @since 2026-10-05 16:08:16
 */
public class DictModule extends SimpleModule {

    public DictModule(DictCache cache) {
        super("CommonToolDictionary");
        setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier bean,
                                                             List<BeanPropertyWriter> properties) {
                Set<String> names = new HashSet<>();
                properties.forEach(property -> names.add(property.getName()));
                List<BeanPropertyWriter> result = new ArrayList<>();
                for (BeanPropertyWriter property : properties) {
                    Dict annotation = property.getAnnotation(Dict.class);
                    if (annotation == null) {
                        result.add(property);
                        continue;
                    }
                    Class<?> type = property.getType().getRawClass();
                    Assert.isTrue(type == String.class || Number.class.isAssignableFrom(type) || type.isPrimitive(),
                            "Dict仅支持字符串和数值属性");
                    String textName = property.getName() + annotation.suffix();
                    Assert.hasText(annotation.value(), "字典类型不能为空");
                    Assert.isTrue(names.add(textName), "字典文本字段与已有字段冲突");
                    result.add(new BeanPropertyWriter(property) {
                        @Override
                        public void serializeAsProperty(Object value, JsonGenerator generator, SerializationContext context)
                                throws Exception {
                            var outputContext = generator.streamWriteContext();
                            int count = outputContext.getEntryCount();
                            super.serializeAsProperty(value, generator, context);
                            if (count == outputContext.getEntryCount()) { return; }
                            Object code = get(value);
                            if (code == null) { return; }
                            String text = cache.getDictText(annotation.value(), code.toString());
                            if (text != null) { generator.writeStringProperty(textName, text); }
                        }
                    });
                }
                return result;
            }
        });
    }
}
