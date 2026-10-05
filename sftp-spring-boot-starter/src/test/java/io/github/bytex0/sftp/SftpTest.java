package io.github.bytex0.sftp;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * SFTP 自动配置安全默认值及惰性连接验证。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
class SftpTest {

    @TempDir
    Path temporary;

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
}
