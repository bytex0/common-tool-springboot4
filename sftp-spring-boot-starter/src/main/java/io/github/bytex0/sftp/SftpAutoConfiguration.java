package io.github.bytex0.sftp;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 不主动连接远端的 SFTP 自动配置。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@AutoConfiguration
@EnableConfigurationProperties(SftpProperties.class)
@ConditionalOnProperty(prefix = "sftp-pool", name = "enable", havingValue = "true")
public class SftpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SftpTemplate sftpTemplate(SftpProperties properties) {
        return new SftpTemplate(properties);
    }
}
