package io.github.bytex0.sftp;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SFTP 连接池安全配置，不生成包含密码的 toString。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@Getter
@Setter
@ConfigurationProperties("sftp-pool")
public class SftpProperties {

    /**
     * 显式开启连接池，启动时不建立网络连接。
     */
    private boolean enable;

    /**
     * SSH 服务地址。
     */
    private String host;

    /**
     * SSH 服务端口。
     */
    private int port = 22;

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
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 池耗尽时最长等待时间。
     */
    private Duration maxWait = Duration.ofSeconds(3);

    /**
     * 最大并发连接数。
     */
    private int maxTotal = 4;
}
