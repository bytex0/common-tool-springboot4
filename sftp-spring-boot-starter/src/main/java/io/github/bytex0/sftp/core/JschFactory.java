package io.github.bytex0.sftp.core;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.github.bytex0.sftp.SftpProperties;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH通道工厂(JschFactory)绑定 Session 所有权、严格校验主机公钥并重置归还通道状态。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:50:50
 */
public class JschFactory implements PooledObjectFactory<ChannelSftp> {

    /**
     * 构造时复制的配置，避免后续外部修改改变已建池的连接身份。
     */
    private final SftpProperties properties;

    /**
     * 每条通道对应的 Session 与初始工作目录，销毁时同时移除。
     */
    private final Map<ChannelSftp, Connection> connections = new ConcurrentHashMap<>();

    /**
     * 保留原构造方式，使用标准 SSH known_hosts 文件，绝不关闭主机校验。
     *
     * @param host 主机
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     */
    public JschFactory(String host, int port, String username, String password) {
        this(SftpProperties.builder().host(host).port(port).username(username).password(password)
                .knownHosts(defaultKnownHosts()).build());
    }

    /**
     * 创建工厂但不建立网络连接。
     *
     * @param properties 包含可信主机文件和认证方式的配置
     */
    public JschFactory(SftpProperties properties) {
        validate(properties);
        this.properties = properties.toBuilder().build();
    }

    /**
     * 返回原无主机文件参数构造方式使用的标准文件位置。
     *
     * @return 用户 SSH known_hosts 文件路径
     */
    static String defaultKnownHosts() {
        return Path.of(System.getProperty("user.home"), ".ssh", "known_hosts").toString();
    }

    /**
     * 校验连接及池配置，所有错误发生在首次连接之前。
     *
     * @param properties 待校验配置
     */
    static void validate(SftpProperties properties) {
        Assert.notNull(properties, "SFTP配置不能为空");
        Assert.hasText(properties.getHost(), "sftp-pool.host is required");
        Assert.hasText(properties.getUsername(), "sftp-pool.username is required");
        Assert.hasText(properties.getKnownHosts(), "sftp-pool.known-hosts is required");
        Assert.isTrue(Files.isRegularFile(Path.of(properties.getKnownHosts())), "known-hosts file is unavailable");
        Assert.isTrue(properties.getPort() != null && properties.getPort() > 0 && properties.getPort() <= 65535,
                "Invalid SFTP port");
        Assert.isTrue(properties.getMaxTotal() != null && properties.getMaxTotal() > 0 && properties.getMaxTotal() <= 128,
                "Invalid pool capacity");
        Assert.isTrue(properties.getMaxIdle() != null && properties.getMaxIdle() >= 0, "max-idle不能为负数");
        Assert.isTrue(properties.getMinIdle() != null && properties.getMinIdle() >= 0
                && properties.getMinIdle() <= Math.min(properties.getMaxIdle(), properties.getMaxTotal()), "min-idle不合法");
        Assert.isTrue(StringUtils.hasText(properties.getPassword()) ^ StringUtils.hasText(properties.getPrivateKey()),
                "Configure exactly one of password or private-key");
        Assert.notNull(properties.getConnectTimeout(), "connect-timeout is required");
        Assert.notNull(properties.getMaxWait(), "max-wait is required");
        Assert.isTrue(properties.getConnectTimeout().toMillis() > 0 && properties.getConnectTimeout().toMillis() <= 120000,
                "connect-timeout must be 1ms to 120s");
        Assert.isTrue(properties.getMaxWait().toMillis() > 0 && properties.getMaxWait().toMillis() <= 120000,
                "max-wait must be 1ms to 120s");
        Assert.isTrue(properties.getMinEvictableIdleTime() != null && !properties.getMinEvictableIdleTime().isNegative(),
                "min-evictable-idle-time不能为负数");
        Assert.isTrue(properties.getEvictionInterval() != null && !properties.getEvictionInterval().isNegative(),
                "eviction-interval不能为负数");
    }

    /**
     * 创建经过主机公钥校验的通道，部分创建失败时关闭 Session 与通道。
     *
     * @return 池对象
     * @throws Exception 认证、网络或工作目录初始化失败
     */
    @Override
    public PooledObject<ChannelSftp> makeObject() throws Exception {
        JSch jsch = new JSch();
        jsch.setKnownHosts(properties.getKnownHosts());
        boolean privateKey = StringUtils.hasText(properties.getPrivateKey());
        if (privateKey) {
            jsch.addIdentity(properties.getPrivateKey());
        }
        Session session = jsch.getSession(properties.getUsername(), properties.getHost(), properties.getPort());
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("PreferredAuthentications", privateKey ? "publickey" : "password");
        session.setPassword(properties.getPassword());
        session.setDaemonThread(true);
        int timeout = Math.toIntExact(properties.getConnectTimeout().toMillis());
        session.setTimeout(timeout);
        ChannelSftp channel = null;
        boolean created = false;
        try {
            session.connect(timeout);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(timeout);
            Connection connection = new Connection(session, channel.pwd(), channel.lpwd());
            connections.put(channel, connection);
            created = true;
            return new DefaultPooledObject<>(channel);
        } finally {
            if (!created) {
                try {
                    if (channel != null) {
                        channel.disconnect();
                    }
                } finally {
                    session.disconnect();
                }
            }
        }
    }

    /**
     * 断线对象不得在旧 Session 上无超时重连，由池销毁后重新创建。
     *
     * @param value 待借出对象
     * @throws JSchException 通道或 Session 已断开
     */
    @Override
    public void activateObject(PooledObject<ChannelSftp> value) throws JSchException {
        if (!validateObject(value)) {
            throw new JSchException("SFTP通道已断开");
        }
    }

    /**
     * 同时检查通道和 Session 状态。
     *
     * @param value 池对象
     * @return 是否仍可使用
     */
    @Override
    public boolean validateObject(PooledObject<ChannelSftp> value) {
        Connection connection = connections.get(value.getObject());
        return connection != null && connection.session().isConnected() && value.getObject().isConnected();
    }

    /**
     * 重置本地与远端工作目录，避免后续借用者继承前一次回调的 cd/lcd。
     *
     * @param value 待归还对象
     * @throws Exception 目录重置失败，池将销毁该对象
     */
    @Override
    public void passivateObject(PooledObject<ChannelSftp> value) throws Exception {
        Connection connection = connections.get(value.getObject());
        Assert.state(connection != null, "未知SFTP通道");
        value.getObject().cd(connection.remoteDirectory());
        value.getObject().lcd(connection.localDirectory());
    }

    /**
     * 销毁通道时始终同时销毁原 Session。
     *
     * @param value 待销毁对象
     */
    @Override
    public void destroyObject(PooledObject<ChannelSftp> value) {
        Connection connection = connections.remove(value.getObject());
        try {
            value.getObject().disconnect();
        } finally {
            if (connection != null) {
                connection.session().disconnect();
            }
        }
    }

    /**
     * 保留原带销毁模式的重载，所有模式都释放完整 SSH 会话。
     *
     * @param value 池对象
     * @param mode 销毁模式
     */
    @Override
    public void destroyObject(PooledObject<ChannelSftp> value, DestroyMode mode) {
        destroyObject(value);
    }

    /**
     * 通道资源(Connection)记录 SSH 会话和初始目录。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:50:50
     * @param session SSH 会话
     * @param remoteDirectory 初始远端目录
     * @param localDirectory 初始本地目录
     */
    private record Connection(
            /**
             * 与通道共同关闭的 SSH 会话。
             */
            Session session,

            /**
             * 初始远端目录。
             */
            String remoteDirectory,

            /**
             * 初始本地目录。
             */
            String localDirectory) {
    }
}
