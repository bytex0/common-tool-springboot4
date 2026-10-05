package io.github.bytex0.sftp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.time.Duration;
import com.jcraft.jsch.ChannelSftp;
import io.github.bytex0.sftp.core.JschConnectionPool;
import io.github.bytex0.sftp.core.SftpInfoProperties;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

/**
 * SFTP 自动配置安全默认值及惰性连接验证。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
class SftpTest {

    /**
     * 独立测试目录，不包含用户的真实 SSH 文件。
     */
    @TempDir
    Path temporary;

    /**
     * 默认关闭，自定义模板不强制提供远端凭据。
     */
    @Test
    void disabledAndUserOverrideDoNotRequireCredentials() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SftpAutoConfiguration.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(SftpTemplate.class));
        runner.withPropertyValues("sftp-pool.enable=false").run(context ->
                assertThat(context).doesNotHaveBean(SftpTemplate.class));
        runner.withPropertyValues("sftp-pool.enable=true").run(context -> assertThat(context).hasFailed());
        SftpTemplate custom = mock(SftpTemplate.class);
        runner.withPropertyValues("sftp-pool.enable=true").withBean(SftpTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(SftpTemplate.class)).isSameAs(custom));
    }

    /**
     * 校验主机信任和池容量，构造及关闭不建立连接。
     *
     * @throws Exception 测试文件准备失败
     */
    @Test
    void validatesTrustAndLimitsWithoutConnecting() throws Exception {
        SftpProperties properties = new SftpProperties();
        properties.setHost("127.0.0.1");
        properties.setUsername("test");
        properties.setPassword("unit-test-only");
        assertThatIllegalArgumentException().isThrownBy(() -> new SftpTemplate(properties));
        properties.setKnownHosts(Files.createFile(temporary.resolve("known_hosts")).toString());
        SftpTemplate template = new SftpTemplate(properties);
        assertThat(template.activeConnections()).isZero();
        template.close();
        assertThatThrownBy(() -> template.list(".")).isInstanceOf(IllegalStateException.class);
        properties.setMaxTotal(0);
        assertThatIllegalArgumentException().isThrownBy(() -> new SftpTemplate(properties));
    }

    /**
     * 恢复原 Builder、全参数构造及默认属性，描述文本不泄露认证材料。
     */
    @Test
    void preservesOriginalPropertiesAndRedactsBuilders() {
        SftpInfoProperties properties = SftpInfoProperties.builder().host("example").password("sensitive-test-value").build();
        assertThat(properties.getPort()).isEqualTo(22);
        assertThat(properties.getMaxTotal()).isEqualTo(3);
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        properties.setConnectTimeout(Duration.ofSeconds(8));
        assertThat(properties.getMaxWait()).isEqualTo(Duration.ofSeconds(8));
        assertThat(properties.toString()).doesNotContain("sensitive-test-value");
        assertThat(properties.toBuilder().toString()).doesNotContain("sensitive-test-value");
        SftpInfoProperties original = new SftpInfoProperties(false, "host", 22, "user", "test-only",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 2, 0, 2);
        assertThat(original.getMaxWait()).isEqualTo(Duration.ofSeconds(2));
    }

    /**
     * 同名池关闭并重建后，旧通道仍归还原池；错误名称不会消费归还机会。
     *
     * @throws Exception 模拟借用失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void returnsToOriginalPoolAfterNamedRebuild() throws Exception {
        JschConnectionPool manager = spy(new JschConnectionPool());
        GenericObjectPool<ChannelSftp> original = mock(GenericObjectPool.class);
        GenericObjectPool<ChannelSftp> replacement = mock(GenericObjectPool.class);
        ChannelSftp channel = mock(ChannelSftp.class);
        when(channel.isConnected()).thenReturn(true);
        when(original.borrowObject()).thenReturn(channel);
        doReturn(original, replacement).when(manager).getPool(any(SftpProperties.class));
        SftpProperties properties = new SftpProperties();
        manager.buildPool("named", properties);
        assertThat(manager.borrowObject("named")).isSameAs(channel);
        assertThatThrownBy(() -> manager.returnObject("wrong", channel)).hasMessageContaining("其他命名池");
        manager.close("named");
        manager.buildPool("named", properties);
        manager.returnObject("named", channel);
        verify(original).returnObject(channel);
        verify(replacement, never()).returnObject(channel);
        assertThat(manager.getPool("named")).isSameAs(replacement);
        manager.close();
        verify(replacement).close();
    }

    /**
     * 断线通道必须销毁并减少借用计数，不能像原实现一样忽略归还。
     *
     * @throws Exception 模拟借用失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void invalidatesDisconnectedBorrower() throws Exception {
        JschConnectionPool manager = spy(new JschConnectionPool());
        GenericObjectPool<ChannelSftp> pool = mock(GenericObjectPool.class);
        ChannelSftp channel = mock(ChannelSftp.class);
        when(pool.borrowObject()).thenReturn(channel);
        doReturn(pool).when(manager).getPool(any(SftpProperties.class));
        manager.buildPool(JschConnectionPool.DEFAULT_KEY, new SftpProperties());
        manager.borrowObject();
        manager.returnObject(channel);
        verify(pool).invalidateObject(channel);
        verify(pool, never()).returnObject(channel);
        assertThatThrownBy(() -> manager.returnObject(channel)).hasMessageContaining("已经归还");
        manager.close();
    }

    /**
     * 回调失败销毁通道，清理失败作为 suppressed 保留，不覆盖原业务异常。
     *
     * @throws Exception 模拟借用失败
     */
    @Test
    void preservesCallbackFailureAndCleanupFailure() throws Exception {
        JschConnectionPool manager = mock(JschConnectionPool.class);
        ChannelSftp channel = mock(ChannelSftp.class);
        when(manager.borrowObject("named")).thenReturn(channel);
        doThrow(new IllegalStateException("cleanup")).when(manager).invalidateObject("named", channel);
        SftpTemplate template = new SftpTemplate(manager);
        assertThatThrownBy(() -> template.execute("named", value -> {
            throw new IOException("business");
        })).isInstanceOf(IOException.class).hasMessage("business")
                .satisfies(error -> assertThat(error.getSuppressed()).extracting(Throwable::getMessage).containsExactly("cleanup"));
        template.close();
        verify(manager, never()).close();
    }

    /**
     * 自动装配提供原池别名与原属性类型，同一个管理器只有一个实例。
     *
     * @throws Exception 测试文件创建失败
     */
    @Test
    void exposesOriginalBeanNamesWithoutAmbiguity() throws Exception {
        Path knownHosts = Files.createFile(temporary.resolve("trusted"));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SftpAutoConfiguration.class))
                .withPropertyValues("sftp-pool.enable=true", "sftp-pool.host=127.0.0.1", "sftp-pool.username=test",
                        "sftp-pool.password=unit-test-only", "sftp-pool.known-hosts=" + knownHosts, "sftp-pool.eviction-interval=0s")
                .run(context -> {
                    assertThat(context).hasSingleBean(JschConnectionPool.class).hasSingleBean(SftpInfoProperties.class);
                    assertThat(context.getBean("defaultConnectionPool")).isSameAs(context.getBean("jschConnectionPool"));
                    assertThat(context.getBean(SftpTemplate.class).activeConnections()).isZero();
                });
    }
}
