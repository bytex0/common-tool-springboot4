package io.github.bytex0.ip2region.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IP 归属地查询结果，保留原 JavaBean 契约和数据库原始字段值。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionResult {

    /**
     * 国家，保留数据库中的空字符串及未知标记。
     */
    private String country;

    /**
     * 区域，保留数据库原始值。
     */
    private String area;

    /**
     * 省份或州。
     */
    private String province;

    /**
     * 城市。
     */
    private String city;

    /**
     * 网络运营商。
     */
    private String isp;

    /**
     * 解析以竖线分隔的五字段记录，未知记录返回 null，损坏记录不静默忽略。
     *
     * @param raw 数据库返回的记录，可为空
     * @return 完整结果或 null
     * @throws IllegalStateException 非空记录的字段数量不是五个
     */
    public static RegionResult fromRawString(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 5) {
            throw new IllegalStateException("Invalid region database result");
        }
        return new RegionResult(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }

    /**
     * 保留此前 Boot 4 版本的 record 风格读取入口。
     *
     * @return 国家
     */
    public String country() {
        return country;
    }

    /**
     * 保留 record 风格读取入口。
     *
     * @return 区域
     */
    public String area() {
        return area;
    }

    /**
     * 保留 record 风格读取入口。
     *
     * @return 省份或州
     */
    public String province() {
        return province;
    }

    /**
     * 保留 record 风格读取入口。
     *
     * @return 城市
     */
    public String city() {
        return city;
    }

    /**
     * 保留 record 风格读取入口。
     *
     * @return 运营商
     */
    public String isp() {
        return isp;
    }
}
