package io.github.bytex0.oss;

import io.github.bytex0.oss.client.OssClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 对象存储配置(OssConfigurationTest)自动装配及资源生命周期测试
 *
 * @author bytex0
 * @since 2026-10-05 14:57:26
 */
class OssConfigurationTest {

    /**
     * 不访问外部服务的自动配置测试上下文
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssConfiguration.class));

    /**
     * 使用 Boot 4 imports 资源发现自动配置。
     */
    @Test
    void shouldRegisterImports() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(OssConfiguration.class.getName());
    }

    /**
     * 默认关闭及显式关闭均不创建外部连接客户端。
     */
    @Test
    void shouldNotConnectByDefaultOrWhenDisabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(OssClient.class));
        runner.withPropertyValues("oss.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean(S3Client.class));
    }

    /**
     * 完整配置只构造客户端，不在启动时访问服务端。
     */
    @Test
    void shouldBuildClientsWithoutConnectingToEndpoint() {
        configured().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OssClient.class).hasSingleBean(S3Client.class)
                    .hasSingleBean(S3Presigner.class);
            assertThat(context.getBean(OssProperties.class).isPathStyleAccess()).isTrue();
        });
    }

    /**
     * 自定义业务客户端时不强制默认凭据与 SDK 客户端。
     */
    @Test
    void shouldBackOffForCustomOssClientWithoutCredentials() {
        OssClient custom = mock(OssClient.class);
        runner.withPropertyValues("oss.enable=true").withBean(OssClient.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OssClient.class).doesNotHaveBean(S3Client.class);
            assertThat(context.getBean(OssClient.class)).isSameAs(custom);
        });
    }

    /**
     * 容器管理的 SDK Bean 保持自定义并在容器退出时关闭。
     */
    @Test
    void shouldSupportCustomSdkClientsAndCloseThem() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        runner.withPropertyValues("oss.enable=true")
                .withBean(S3Client.class, () -> client).withBean(S3Presigner.class, () -> presigner)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(OssClient.class));
        verify(client).close();
        verify(presigner).close();
    }

    /**
     * 业务凭据提供器优先于配置密钥。
     */
    @Test
    void shouldUseCustomCredentialsProvider() {
        AwsCredentialsProvider provider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test-access", "test-secret"));
        runner.withPropertyValues("oss.enable=true", "oss.endpoint=http://127.0.0.1:1")
                .withBean(AwsCredentialsProvider.class, () -> provider)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(OssClient.class));
    }

    /**
     * 缺失凭据时启动明确失败。
     */
    @Test
    void shouldRejectMissingCredentials() {
        runner.withPropertyValues("oss.enable=true", "oss.endpoint=http://127.0.0.1:1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("oss.access-key 不能为空");
        });
    }

    /**
     * 拒绝非法连接和分片参数，不通过 SDK 延迟暴露配置错误。
     */
    @Test
    void shouldRejectInvalidConnectionSettings() {
        configured().withPropertyValues("oss.endpoint=ftp://localhost").run(context ->
                assertThat(context).hasFailed());
        configured().withPropertyValues("oss.max-connections=0").run(context ->
                assertThat(context).hasFailed());
        configured().withPropertyValues("oss.socket-timeout=0s").run(context ->
                assertThat(context).hasFailed());
        configured().withPropertyValues("oss.multipart-threshold=1MB").run(context ->
                assertThat(context).hasFailed());
        configured().withPropertyValues("oss.multipart-part-size=6GB").run(context ->
                assertThat(context).hasFailed());
    }

    /**
     * SDK 类型被排除时自动配置退让。
     */
    @Test
    void shouldBackOffWithoutSdkClasses() {
        runner.withClassLoader(new FilteredClassLoader("software.amazon.awssdk.services.s3"))
                .withPropertyValues("oss.enable=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(OssClient.class));
    }

    /**
     * 提供不会发起连接的测试配置，不包含真实凭据。
     *
     * @return 具有显式测试设置的上下文
     */
    private ApplicationContextRunner configured() {
        return runner.withPropertyValues("oss.enable=true", "oss.endpoint=http://127.0.0.1:1",
                "oss.access-key=test-access", "oss.access-secret=test-secret");
    }
}
