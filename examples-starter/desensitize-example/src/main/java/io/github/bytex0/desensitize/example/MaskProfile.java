package io.github.bytex0.desensitize.example;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.enums.DesensitizeType;

/**
 * 脱敏样例(MaskProfile)固定合成数据
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
public class MaskProfile {

    /**
     * 合成手机号
     */
    @Desensitize(type = DesensitizeType.PHONE)
    public String phone = "13800138000";

    /**
     * 合成姓名
     */
    @Desensitize(type = DesensitizeType.NAME)
    public String name = "张三";

    /**
     * 合成邮箱
     */
    @Desensitize(type = DesensitizeType.EMAIL)
    public String email = "alice@example.com";

    /**
     * 自定义范围
     */
    @Desensitize(type = DesensitizeType.MASK_ALL, startIndex = 1, maskChar = "#")
    public String range = "ABCDE";

    /**
     * 自定义Bean策略
     */
    @Desensitize(type = DesensitizeType.CUSTOM, handler = CustomMaskHandler.class)
    public String custom = "private";

    /**
     * 不应受其他属性规则影响的普通文本
     */
    public String ordinary = "public";
}
