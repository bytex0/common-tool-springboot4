package io.github.bytex0.sftp;

import io.github.bytex0.sftp.core.JschConnectionPool;
import io.github.bytex0.sftp.core.SftpInfoProperties;

/**
 * 原配置工厂(SftpConfiguration)保留直接构造及两个池工厂入口，自动装配由 SftpAutoConfiguration 负责。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:50:50
 */
public class SftpConfiguration {

    /**
     * 当前连接配置。
     */
    private final SftpInfoProperties properties;

    /**
     * 保留原属性构造器。
     *
     * @param properties 完整安全配置
     */
    public SftpConfiguration(SftpInfoProperties properties) {
        this.properties = properties;
    }

    /**
     * 保留原空命名池工厂，调用方拥有管理器生命周期。
     *
     * @return 空管理器
     */
    public JschConnectionPool defaultConnectionPool() {
        return new JschConnectionPool(properties.getMaxPools());
    }

    /**
     * 保留原默认连接池工厂，不提前建连。
     *
     * @return 拥有默认池的管理器，调用方负责关闭
     */
    public JschConnectionPool jschConnectionPool() {
        return new JschConnectionPool(properties);
    }
}
