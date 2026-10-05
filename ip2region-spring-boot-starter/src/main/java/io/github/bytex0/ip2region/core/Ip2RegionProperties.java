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
     * 显式开启本地 IPv4 数据库查询。
     */
    private boolean enabled;

    /**
     * 文件路径或 classpath: 资源位置。
     */
    private String dbPath;

    /**
     * 数据文件最大字节数，避免错误配置耗尽堆。
     */
    private int maxDatabaseBytes = 64 * 1024 * 1024;
}
