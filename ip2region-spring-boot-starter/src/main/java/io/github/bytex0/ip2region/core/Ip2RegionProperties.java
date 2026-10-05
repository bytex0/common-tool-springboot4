package io.github.bytex0.ip2region.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP 数据库加载配置。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
@Data
@ConfigurationProperties("ip2region")
public class Ip2RegionProperties {

    /**
     * 保留原属性模型的 true 默认值；自动配置仍要求外部显式设置 enabled=true。
     */
    private Boolean enabled = true;

    /**
     * 文件路径或 classpath: 资源位置。
     */
    private String dbPath;

    /**
     * 数据文件最大字节数，默认 64 MiB，允许范围为 XDB 索引头大小至 256 MiB。
     */
    private int maxDatabaseBytes = 64 * 1024 * 1024;

    /**
     * 保留此前版本的判断入口，包装类型避免破坏原 Boolean JavaBean setter。
     *
     * @return 非空布尔值，仅配置值为 true 时返回 true
     */
    public Boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
