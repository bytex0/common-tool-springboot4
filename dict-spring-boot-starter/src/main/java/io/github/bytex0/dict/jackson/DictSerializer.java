package io.github.bytex0.dict.jackson;

import io.github.bytex0.dict.DictCache;
import io.github.bytex0.dict.annotation.Dict;
import org.springframework.util.Assert;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 字典字符串序列化(DictSerializer)保留原上下文扩展入口，缓存归属实例而非全局静态状态。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:07:34
 */
public class DictSerializer extends StdSerializer<String> {

    /**
     * 序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 可显式指定的缓存，空时从本次序列化上下文获取。
     */
    private final DictCache cache;

    /**
     * 当前属性注解，普通字符串及根字符串为空。
     */
    private final Dict annotation;

    /**
     * 当前实际 JSON 文本属性名。
     */
    private final String textName;

    /**
     * 保留原无参构造；注解使用时由 DictModule 或 ObjectWriter 属性提供 DictCache。
     */
    public DictSerializer() {
        this(null);
    }

    /**
     * 单独注册序列化器时显式指定缓存。
     *
     * @param cache 实例缓存，可为空
     */
    public DictSerializer(DictCache cache) {
        this(cache, null, null);
    }

    /**
     * 创建不可变的属性上下文实例。
     *
     * @param cache 实例缓存
     * @param annotation 属性注解
     * @param textName 实际追加属性名
     */
    private DictSerializer(DictCache cache, Dict annotation, String textName) {
        super(String.class);
        this.cache = cache;
        this.annotation = annotation;
        this.textName = textName;
    }

    /**
     * 先写原编码，仅注解属性追加字典文本，不影响普通字符串或 Map 值。
     *
     * @param value 原编码
     * @param generator JSON 输出
     * @param context 当前序列化上下文
     */
    @Override
    public void serialize(String value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(value);
        if (annotation == null || value == null) {
            return;
        }
        DictCache current = cache == null ? (DictCache) context.getAttribute(DictCache.class) : cache;
        Assert.state(current != null, "DictSerializer需要实例DictCache或字典模块上下文");
        String text = current.getDictText(annotation.value(), value, annotation.table(), annotation.field(), annotation.codeField());
        if (text != null) {
            generator.writeStringProperty(textName, text);
        }
    }

    /**
     * 每个属性使用独立实例，不能将前一个属性的注解泄漏到普通字符串。
     *
     * @param context 当前上下文
     * @param property 当前属性，根值时为空
     * @return 属性专用序列化器
     */
    @Override
    public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
        Dict dict = property == null ? null : property.getAnnotation(Dict.class);
        return new DictSerializer(cache, dict, dict == null ? null : property.getName() + dict.suffix());
    }

    /**
     * 保持字符串 NON_EMPTY 省略规则。
     *
     * @param context 当前上下文
     * @param value 原编码
     * @return 是否为空
     */
    @Override
    public boolean isEmpty(SerializationContext context, String value) {
        return value == null || value.isEmpty();
    }
}
