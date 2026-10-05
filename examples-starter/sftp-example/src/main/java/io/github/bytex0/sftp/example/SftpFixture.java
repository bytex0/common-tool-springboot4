package io.github.bytex0.sftp.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * 仅供自动化独立进程使用的真实 SSH/SFTP 协议服务器。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@Configuration(proxyBeanMethods = false)
@Profile("sftp-fixture")
public class SftpFixture {

    /**
     * 在独立进程中启动真实协议服务，只使用本次临时目录和随机密码。
     *
     * @param environment 测试环境
     * @return 容器退出时关闭的 SSH 服务
     * @throws Exception 服务启动或公钥文件写入失败
     */
    @Bean(destroyMethod = "stop")
    SshServer fixtureServer(Environment environment) throws Exception {
        Path root = Path.of(environment.getRequiredProperty("TEST_SFTP_ROOT"));
        Files.createDirectories(root.resolve("data/upload"));
        String username = environment.getRequiredProperty("TEST_SFTP_USERNAME");
        String password = environment.getRequiredProperty("TEST_SFTP_PASSWORD");
        SimpleGeneratorHostKeyProvider keys = new SimpleGeneratorHostKeyProvider(root.resolve("host-key"));
        keys.setAlgorithm("RSA");
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(keys);
        server.setPasswordAuthenticator((user, supplied, session) -> username.equals(user) && password.equals(supplied));
        server.setFileSystemFactory(new VirtualFileSystemFactory(root.resolve("data")));
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        try {
            server.start();
            String key = PublicKeyEntry.toString(keys.loadKeys(null).iterator().next().getPublic());
            Files.writeString(root.resolve("known_hosts"), "[127.0.0.1]:" + server.getPort() + " " + key + "\n");
            Files.writeString(root.resolve("port"), Integer.toString(server.getPort()));
            return server;
        } catch (Exception failure) {
            try {
                server.stop(true);
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
}
