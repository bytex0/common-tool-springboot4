package io.github.bytex0.sftp;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SFTP 连接池安全配置，不生成包含密码的 toString。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ConfigurationProperties("sftp-pool")
public class SftpProperties {

    /**
     * 显式开启连接池，启动时不建立网络连接。
     */
    @Builder.Default
    private Boolean enable = false;

    /**
     * SSH 服务地址。
     */
    private String host;

    /**
     * SSH 服务端口。
     */
    @Builder.Default
    private Integer port = 22;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 密码认证凭据，通过外部配置提供。
     */
    private String password;

    /**
     * 可选私钥文件，与密码认证二选一。
     */
    private String privateKey;

    /**
     * 必填的受信任主机公钥文件。
     */
    private String knownHosts;

    /**
     * TCP、SSH 握手和读写超时。
     */
    @Builder.Default
    private Duration connectTimeout = Duration.ofSeconds(3);

    /**
     * 池耗尽时最长等待时间，未显式设置时沿用原 connectTimeout 配置。
     */
    @Builder.Default
    private Duration maxWait = null;

    /**
     * 最大并发连接数。
     */
    @Builder.Default
    private Integer maxTotal = 3;

    /**
     * 最大空闲连接数，默认 3，实际不超过 maxTotal。
     */
    @Builder.Default
    private Integer maxIdle = 3;

    /**
     * 最小空闲连接数，默认 1；不会在构造时主动建连。
     */
    @Builder.Default
    private Integer minIdle = 1;

    /**
     * 空闲连接可被驱逐的最小时长，恢复原默认 5 秒。
     */
    @Builder.Default
    private Duration minEvictableIdleTime = Duration.ofSeconds(5);

    /**
     * 空闲维护间隔，默认 30 秒；零表示关闭后台维护，不在构造时预热。
     */
    @Builder.Default
    private Duration evictionInterval = Duration.ofSeconds(30);

    /**
     * 当前管理器最多登记的命名池数量，默认 32，包含默认池。
     */
    @Builder.Default
    private int maxPools = 32;

    /**
     * 保留当前版本的启用判断方法。
     *
     * @return null 视为未启用
     */
    public Boolean isEnable() {
        return Boolean.TRUE.equals(enable);
    }

    /**
     * 保留原连接超时同时控制借用等待的默认行为，显式 maxWait 优先。
     *
     * @return 最长等待时长
     */
    public Duration getMaxWait() {
        return maxWait == null ? connectTimeout : maxWait;
    }

    /**
     * 配置构建器(SftpPropertiesBuilderImpl)隐藏 Lombok 抽象构建器中的认证字段描述。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:50:50
     */
    private static final class SftpPropertiesBuilderImpl extends SftpPropertiesBuilder<SftpProperties, SftpPropertiesBuilderImpl> {

        /**
         * 只输出类型，不输出任何连接配置或凭据。
         *
         * @return 安全描述
         */
        @Override
        public String toString() {
            return "SftpPropertiesBuilder[redacted]";
        }
    }
}
