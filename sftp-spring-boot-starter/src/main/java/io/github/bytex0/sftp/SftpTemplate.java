package io.github.bytex0.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 有界 SFTP 连接池，操作结束自动归还或销毁连接，始终校验主机公钥。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
public class SftpTemplate implements AutoCloseable {

    private final GenericObjectPool<Connection> pool;

    /**
     * 通道与 Session 具有共同所有权。
     *
     * @author bytex0
     * @since 2026-10-05 19:47:49
     */
    private record Connection(Session session, ChannelSftp channel) {}

    /**
     * 操作仅在借用作用域内执行，不允许逃逸 SDK 资源。
     *
     * @author bytex0
     * @since 2026-10-05 19:47:49
     */
    private interface Operation<T> {
        T apply(ChannelSftp channel) throws Exception;
    }

    public SftpTemplate(SftpProperties properties) {
        Assert.hasText(properties.getHost(), "sftp-pool.host is required");
        Assert.hasText(properties.getUsername(), "sftp-pool.username is required");
        Assert.hasText(properties.getKnownHosts(), "sftp-pool.known-hosts is required");
        Assert.isTrue(Files.isRegularFile(Path.of(properties.getKnownHosts())), "known-hosts file is unavailable");
        Assert.isTrue(properties.getPort() > 0 && properties.getPort() <= 65535, "Invalid SFTP port");
        Assert.isTrue(properties.getMaxTotal() > 0 && properties.getMaxTotal() <= 128, "Invalid pool capacity");
        Assert.isTrue(StringUtils.hasText(properties.getPassword()) ^ StringUtils.hasText(properties.getPrivateKey()),
                "Configure exactly one of password or private-key");
        Assert.notNull(properties.getConnectTimeout(), "connect-timeout is required");
        Assert.notNull(properties.getMaxWait(), "max-wait is required");
        long milliseconds = properties.getConnectTimeout().toMillis();
        Assert.isTrue(milliseconds > 0 && milliseconds <= 120000, "connect-timeout must be 1ms to 120s");
        Assert.isTrue(properties.getMaxWait().toMillis() > 0 && properties.getMaxWait().toMillis() <= 120000,
                "max-wait must be 1ms to 120s");
        int timeout = (int) milliseconds;
        String host = properties.getHost();
        int port = properties.getPort();
        String username = properties.getUsername();
        String password = properties.getPassword();
        String privateKey = properties.getPrivateKey();
        String knownHosts = properties.getKnownHosts();
        GenericObjectPoolConfig<Connection> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(properties.getMaxTotal());
        config.setMaxIdle(properties.getMaxTotal());
        config.setMinIdle(0);
        config.setMaxWait(properties.getMaxWait());
        config.setTestOnBorrow(true);
        pool = new GenericObjectPool<>(new BasePooledObjectFactory<>() {
            @Override
            public Connection create() throws Exception {
                JSch jsch = new JSch();
                jsch.setKnownHosts(knownHosts);
                if (StringUtils.hasText(privateKey)) {
                    jsch.addIdentity(privateKey);
                }
                Session session = jsch.getSession(username, host, port);
                session.setConfig("StrictHostKeyChecking", "yes");
                session.setConfig("PreferredAuthentications", StringUtils.hasText(privateKey) ? "publickey" : "password");
                session.setPassword(password);
                session.setTimeout(timeout);
                ChannelSftp channel = null;
                try {
                    session.connect(timeout);
                    channel = (ChannelSftp) session.openChannel("sftp");
                    channel.connect(timeout);
                    return new Connection(session, channel);
                } catch (Exception | Error failure) {
                    if (channel != null) {
                        channel.disconnect();
                    }
                    session.disconnect();
                    throw failure;
                }
            }

            @Override
            public PooledObject<Connection> wrap(Connection connection) {
                return new DefaultPooledObject<>(connection);
            }

            @Override
            public boolean validateObject(PooledObject<Connection> value) {
                return value.getObject().channel().isConnected() && value.getObject().session().isConnected();
            }

            @Override
            public void destroyObject(PooledObject<Connection> value) {
                try {
                    value.getObject().channel().disconnect();
                } finally {
                    value.getObject().session().disconnect();
                }
            }
        }, config);
    }

    public void upload(String path, InputStream input) throws Exception {
        execute(channel -> { channel.put(input, path); return null; });
    }

    public void download(String path, OutputStream output) throws Exception {
        execute(channel -> { channel.get(path, output); return null; });
    }

    public void delete(String path) throws Exception {
        execute(channel -> { channel.rm(path); return null; });
    }

    public List<String> list(String path) throws Exception {
        return execute(channel -> {
            List<String> result = new ArrayList<>();
            channel.ls(path, entry -> {
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    result.add(entry.getFilename());
                }
                return ChannelSftp.LsEntrySelector.CONTINUE;
            });
            return List.copyOf(result);
        });
    }

    public int activeConnections() {
        return pool.getNumActive();
    }

    private <T> T execute(Operation<T> operation) throws Exception {
        Connection connection = pool.borrowObject();
        try {
            T result = operation.apply(connection.channel());
            pool.returnObject(connection);
            return result;
        } catch (Exception | Error failure) {
            try {
                pool.invalidateObject(connection);
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public void close() {
        pool.close();
    }
}
