package io.github.bytex0.sftp;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.bytex0.sftp.core.JschConnectionPool;
import io.github.bytex0.sftp.core.SftpInfoProperties;

/**
 * 不主动连接远端的 SFTP 自动配置。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@AutoConfiguration
@EnableConfigurationProperties(SftpInfoProperties.class)
@ConditionalOnProperty(prefix = "sftp-pool", name = "enable", havingValue = "true")
public class SftpAutoConfiguration {

    /**
     * 保留此前直接模板构造入口。
     *
     * @param properties 连接配置
     * @return 独立管理默认池的模板
     */
    public SftpTemplate sftpTemplate(SftpProperties properties) {
        return new SftpTemplate(properties);
    }

    /**
     * 默认池配置(DefaultPools)在用户提供整个模板时整体退让。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:50:50
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(SftpTemplate.class)
    static class DefaultPools {

        /**
         * 注册可替换的命名池管理器，构造时不连接。
         *
         * @param properties 安全配置
         * @return 管理器
         */
        @Bean(name = {"jschConnectionPool", "defaultConnectionPool"}, destroyMethod = "close")
        @ConditionalOnMissingBean
        JschConnectionPool jschConnectionPool(SftpProperties properties) {
            return new JschConnectionPool(properties);
        }

        /**
         * 注册共享池作用域模板，不重复接管管理器关闭。
         *
         * @param pools 命名池管理器
         * @return 模板
         */
        @Bean
        SftpTemplate sftpTemplate(JschConnectionPool pools) {
            return new SftpTemplate(pools);
        }
    }
}
