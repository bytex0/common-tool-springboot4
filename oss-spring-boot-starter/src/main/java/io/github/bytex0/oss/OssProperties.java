package io.github.bytex0.oss;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.net.URI;
import java.time.Duration;

/**
 * 对象存储(OssProperties)连接配置
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /**
     * 是否启用对象存储，默认不创建客户端或连接外部服务
     */
    private boolean enable;

    /**
     * 访问密钥 ID；使用自定义 AwsCredentialsProvider 时可以不配置
     */
    private String accessKey;

    /**
     * 访问密钥密码；仅从环境或外部配置读取，不输出到日志
     */
    private String accessSecret;

    /**
     * S3 API 地址，例如 http://127.0.0.1:19000
     */
    private URI endpoint;

    /**
     * 签名区域，S3 兼容服务通常使用 us-east-1
     */
    private String region = "us-east-1";

    /**
     * 是否使用 endpoint/bucket/key 路径风格
     */
    private boolean pathStyleAccess = true;

    /**
     * HTTP 连接池最大连接数
     */
    private int maxConnections = 100;

    /**
     * 示例及业务侧使用的默认桶，Starter 不会自动创建
     */
    private String bucketName;

    /**
     * 是否禁用 AWS 流式分块编码，默认禁用以适配更多 S3 服务
     */
    private boolean chunkedEncodingDisabled = true;

    /**
     * HTTP 建立连接超时
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * HTTP 读取超时
     */
    private Duration socketTimeout = Duration.ofSeconds(60);

    /**
     * 单次 API 调用包含重试的总超时
     */
    private Duration apiCallTimeout = Duration.ofMinutes(5);

    void validateConnection() {
        Assert.notNull(endpoint, "oss.endpoint 不能为空");
        Assert.isTrue(("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                        && endpoint.getHost() != null && endpoint.getUserInfo() == null
                        && endpoint.getQuery() == null && endpoint.getFragment() == null,
                "oss.endpoint 必须是无凭据、查询参数和片段的 HTTP(S) 服务地址");
        Assert.isTrue(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()),
                "oss.endpoint 不能包含桶或对象路径");
        Assert.hasText(region, "oss.region 不能为空");
        Assert.isTrue(maxConnections > 0, "oss.max-connections 必须大于 0");
        requirePositive(connectionTimeout, "oss.connection-timeout");
        requirePositive(socketTimeout, "oss.socket-timeout");
        requirePositive(apiCallTimeout, "oss.api-call-timeout");
    }

    private void requirePositive(Duration duration, String name) {
        Assert.isTrue(duration != null && duration.compareTo(Duration.ZERO) > 0, name + " 必须大于 0");
    }
}
