package io.github.bytex0.dict.jackson;

import io.github.bytex0.dict.DictCache;
import io.github.bytex0.dict.annotation.Dict;
import org.springframework.util.Assert;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;

/**
 * 字典JSON(DictModule)尊重属性名称、包含规则和已有序列化器
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
public class DictModule extends SimpleModule {

    /**
     * 模块序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 原模块包装构造器的委托对象，普通缓存构造方式为空。
     */
    private final JacksonModule delegate;

    /**
     * 基于实例缓存增强属性，保留既有序列化器和包含规则。
     *
     * @param cache 字典缓存
     */
    public DictModule(DictCache cache) {
        super("DictModule");
        Objects.requireNonNull(cache);
        this.delegate = null;
        setSerializerModifier(new ValueSerializerModifier() {

            /**
             * {@inheritDoc}
             */
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

                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        public void serializeAsProperty(Object value, JsonGenerator generator, SerializationContext context)
                                throws Exception {
                            var outputContext = generator.streamWriteContext();
                            int count = outputContext.getEntryCount();
                            Object previous = context.getAttribute(DictCache.class);
                            context.setAttribute(DictCache.class, cache);
                            try {
                                super.serializeAsProperty(value, generator, context);
                            } finally {
                                context.setAttribute(DictCache.class, previous);
                            }
                            int written = outputContext.getEntryCount() - count;
                            if (written != 1) {
                                return;
                            }
                            Object code = get(value);
                            if (code == null) {
                                return;
                            }
                            String text = cache.getDictText(annotation.value(), code.toString(),
                                    annotation.table(), annotation.field(), annotation.codeField());
                            if (text != null) {
                                generator.writeStringProperty(textName, text);
                            }
                        }
                    });
                }
                return result;
            }
        });
    }

    /**
     * 保留原模块包装构造能力，Jackson 2 Module 类型替换为 Jackson 3 JacksonModule。
     * 内部 DictSerializer 应显式传入缓存或由 ObjectWriter 提供缓存上下文。
     *
     * @param module 待包装模块
     */
    public DictModule(JacksonModule module) {
        super("DictModule");
        this.delegate = Objects.requireNonNull(module);
    }

    /**
     * 注册自身属性增强或原委托模块。
     *
     * @param context Jackson 模块注册上下文
     */
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        if (delegate != null) {
            delegate.setupModule(context);
        }
    }
}
