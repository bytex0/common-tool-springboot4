package io.github.bytex0.desensitize.enums;

/**
 * 脱敏类型(DesensitizeType)内置策略
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
public enum DesensitizeType {
    /**
     * 手机号
     */
    PHONE,
    /**
     * 邮箱
     */
    EMAIL,
    /**
     * 姓名
     */
    NAME,
    /**
     * 身份证
     */
    ID_CARD,
    /**
     * 银行卡
     */
    BANK_CARD,
    /**
     * 地址
     */
    ADDRESS,
    /**
     * 密码
     */
    PASSWORD,
    /**
     * 车牌
     */
    CAR_NUMBER,
    /**
     * 固话
     */
    FIXED_PHONE,
    /**
     * IPv4
     */
    IPV4,
    /**
     * IPv6，默认全遮蔽
     */
    IPV6,
    /**
     * 护照
     */
    PASSPORT,
    /**
     * 军官证，默认全遮蔽
     */
    MILITARY_ID,
    /**
     * 联行号，默认全遮蔽
     */
    CNAPS_CODE,
    /**
     * 全遮蔽
     */
    MASK_ALL,
    /**
     * 自定义Bean
     */
    CUSTOM,
    /**
     * 域名
     */
    DOMAIN
}
