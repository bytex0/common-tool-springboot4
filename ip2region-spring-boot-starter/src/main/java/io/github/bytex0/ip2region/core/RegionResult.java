package io.github.bytex0.ip2region.core;

/**
 * IP 归属地不可变查询结果，保留数据库原始字段值。
 *
 * @param country 国家
 * @param area 区域
 * @param province 省份
 * @param city 城市
 * @param isp 运营商
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
public record RegionResult(String country, String area, String province, String city, String isp) {

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
}
