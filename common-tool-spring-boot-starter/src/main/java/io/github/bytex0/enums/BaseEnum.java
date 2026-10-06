package io.github.bytex0.enums;

import java.util.Objects;

/**
 * 带显式业务编码和名称的枚举契约，不以 ordinal 作为业务编码。
 *
 * @param <T> 实现该契约的枚举类型
 * @author bytex0
 * @since 2026-10-06 14:46:02
 */
public interface BaseEnum<T extends Enum<T> & BaseEnum<T>> {

    /**
     * 按声明顺序查找第一个编码匹配的枚举项。
     *
     * @param type 枚举类型，不得为空
     * @param code 业务编码，为空返回 null
     * @param <T> 枚举类型参数
     * @return 匹配枚举项，未匹配返回 null
     */
    static <T extends Enum<T> & BaseEnum<T>> T parseByCode(Class<T> type, Integer code) {
        Objects.requireNonNull(type, "type");
        if (code == null) {
            return null;
        }
        for (T value : type.getEnumConstants()) {
            if (code.equals(value.getCode())) {
                return value;
            }
        }
        return null;
    }

    /**
     * 获取显式业务编码。
     *
     * @return 业务编码，不应使用序号替代
     */
    Integer getCode();

    /**
     * 获取业务展示名称。
     *
     * @return 业务名称
     */
    String getName();
}
