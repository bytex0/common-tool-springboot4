package io.github.bytex0.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 JSON 工具，保留原入口并在失败时抛出异常，不返回伪成功空值。
 *
 * @author bytex0
 * @since 2026-10-06 14:41:36
 */
public final class JacksonUtil {

    /**
     * 构建后不可变的线程安全映射器，不引用 Spring 应用上下文。
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL))
            .build();

    /**
     * 保留原公开无参构造入口，工具本身不保存实例状态。
     */
    public JacksonUtil() {
    }

    /**
     * 序列化对象，忽略对象中为 null 的属性。
     *
     * @param object 待序列化对象，可为空
     * @return JSON 文本
     */
    public static String toJson(Object object) {
        return MAPPER.writeValueAsString(object);
    }

    /**
     * 解析指定类型的 JSON，失败直接抛出 Jackson 异常。
     *
     * @param json JSON 文本
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 解析对象，JSON null 对应 null
     */
    public static <T> T toObject(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    /**
     * 解析包含泛型信息的 JSON。
     *
     * @param json JSON 文本
     * @param type 目标泛型
     * @param <T> 目标类型参数
     * @return 解析对象
     */
    public static <T> T toObject(String json, TypeReference<T> type) {
        return MAPPER.readValue(json, type);
    }

    /**
     * 按 Jackson 映射规则转换对象。
     *
     * @param object 原对象
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 转换结果
     */
    public static <T> T convertValue(Object object, Class<T> type) {
        return MAPPER.convertValue(object, type);
    }

    /**
     * 按 Jackson 泛型映射规则转换对象。
     *
     * @param object 原对象
     * @param type 目标泛型
     * @param <T> 目标类型参数
     * @return 转换结果
     */
    public static <T> T convertValue(Object object, TypeReference<T> type) {
        return MAPPER.convertValue(object, type);
    }
}
