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

    public DesensitizeUtil(JsonMapper mapper) { this.mapper = mapper; }

    public String toJson(Object value) { return mapper.writeValueAsString(value); }

    public <T> T toObject(String json, Class<T> type) { return mapper.readValue(json, type); }

    public <T> T toObject(String json, TypeReference<T> type) { return mapper.readValue(json, type); }

    public <T> T convertObject(Object value, Class<T> type) { return mapper.convertValue(value, type); }

    public <T> T convertObject(Object value, TypeReference<T> type) { return mapper.convertValue(value, type); }
}
