package io.github.bytex0.sftp.core;

import io.github.bytex0.sftp.SftpProperties;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 原SFTP配置(SftpInfoProperties)保留类型、Builder、可写属性与原全参数构造方式。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:50:50
 */
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ConfigurationProperties("sftp-pool")
public class SftpInfoProperties extends SftpProperties {

    /**
     * 保留原十参数构造器，新安全选项仍须配置 knownHosts 后才能建立连接。
     *
     * @param enable 是否启用
     * @param host 主机
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @param connectTimeout 连接超时，同时作为原等待超时
     * @param minEvictableIdleTime 空闲驱逐时间
     * @param maxIdle 最大空闲数
     * @param minIdle 最小空闲数
     * @param maxTotal 最大连接数
     */
    public SftpInfoProperties(Boolean enable, String host, Integer port, String username, String password,
                              Duration connectTimeout, Duration minEvictableIdleTime,
                              Integer maxIdle, Integer minIdle, Integer maxTotal) {
        setEnable(enable);
        setHost(host);
        setPort(port);
        setUsername(username);
        setPassword(password);
        setConnectTimeout(connectTimeout);
        setMaxWait(connectTimeout);
        setMinEvictableIdleTime(minEvictableIdleTime);
        setMaxIdle(maxIdle);
        setMinIdle(minIdle);
        setMaxTotal(maxTotal);
    }

    /**
     * 原配置构建器(SftpInfoPropertiesBuilderImpl)避免生成的父构建器描述包含密码。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:50:50
     */
    private static final class SftpInfoPropertiesBuilderImpl
            extends SftpInfoPropertiesBuilder<SftpInfoProperties, SftpInfoPropertiesBuilderImpl> {

        /**
         * 不输出连接配置或认证信息。
         *
         * @return 安全描述
         */
        @Override
        public String toString() {
            return "SftpInfoPropertiesBuilder[redacted]";
        }
    }
}
