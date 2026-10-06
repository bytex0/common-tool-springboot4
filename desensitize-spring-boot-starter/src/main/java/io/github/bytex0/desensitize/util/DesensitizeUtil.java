package io.github.bytex0.desensitize.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 脱敏JSON(DesensitizeUtil)实例工具，使用应用配置的Jackson 3映射器
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
public class DesensitizeUtil {

    /**
     * 已注册脱敏模块的应用映射器
     */
    private final JsonMapper mapper;

    /**
     * 接收已按实例配置的映射器，不写入静态状态。
     *
     * @param mapper Jackson 3 映射器
     */
    public DesensitizeUtil(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 序列化对象并应用已注册的脱敏模块，不改变源对象。
     *
     * @param value 原始对象
     * @return JSON 字符串
     */
    public String toJson(Object value) {
        return mapper.writeValueAsString(value);
    }

    /**
     * 按明确类型读取 JSON，和原实现一致，不把反序列化当作脱敏操作。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 模型类型
     * @return 解析结果，失败抛出异常
     */
    public <T> T toObject(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    /**
     * 按泛型类型读取 JSON，不隐式修改输入字段。
     *
     * @param json JSON 字符串
     * @param type 泛型描述
     * @param <T> 模型类型
     * @return 解析结果
     */
    public <T> T toObject(String json, TypeReference<T> type) {
        return mapper.readValue(json, type);
    }

    /**
     * 通过序列化规则复制转换对象，源值不变。
     *
     * @param value 源对象
     * @param type 目标类型
     * @param <T> 模型类型
     * @return 转换后的对象
     */
    public <T> T convertObject(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    /**
     * 通过脱敏序列化规则进行泛型对象转换。
     *
     * @param value 源对象
     * @param type 泛型描述
     * @param <T> 模型类型
     * @return 转换后的对象
     */
    public <T> T convertObject(Object value, TypeReference<T> type) {
        return mapper.convertValue(value, type);
    }
}
