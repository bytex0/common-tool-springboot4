package io.github.bytex0.sftp.core;

import com.jcraft.jsch.ChannelSftp;
import io.github.bytex0.sftp.SftpProperties;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SFTP命名池(JschConnectionPool)恢复动态建池与原借还入口，并按实际借出池追踪资源。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:50:50
 */
public class JschConnectionPool implements AutoCloseable {

    /**
     * 原默认池键。
     */
    public static final String DEFAULT_KEY = "default";

    /**
     * 注册的命名池，不保存已经关闭的池。
     */
    private final Map<String, GenericObjectPool<ChannelSftp>> pools = new ConcurrentHashMap<>();

    /**
     * 通过本管理器借出的通道及原池，防止同名池重建后归还到错误实例。
     */
    private final Map<ChannelSftp, Borrowed> borrowed = new ConcurrentHashMap<>();

    /**
     * 仅串行化注册/关闭，不在持锁时连接、借用或关闭 SSH。
     */
    private final ReentrantLock lifecycle = new ReentrantLock();

    /**
     * 注册容量上限。
     */
    private final int maxPools;

    /**
     * 在生命周期锁内改变，读取具有可见性。
     */
    private volatile boolean closed;

    /**
     * 保留原空管理器构造方式，可后续动态建池。
     */
    public JschConnectionPool() {
        this(32);
    }

    /**
     * 创建有界空管理器。
     *
     * @param maxPools 最大注册池数
     */
    public JschConnectionPool(int maxPools) {
        Assert.isTrue(maxPools > 0, "SFTP命名池上限必须大于零");
        this.maxPools = maxPools;
    }

    /**
     * 根据安全配置创建默认池，不提前建立连接。
     *
     * @param properties 连接与池设置
     */
    public JschConnectionPool(SftpProperties properties) {
        this(properties.getMaxPools());
        buildPool(DEFAULT_KEY, properties);
    }

    /**
     * 保留原九参数构造方式，主机公钥读取标准 known_hosts 文件。
     *
     * @param host 主机
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @param maxTotal 最大连接数
     * @param minIdle 最小空闲数
     * @param maxIdle 最大空闲数
     * @param timeout 原连接及池等待时长
     * @param minEvictableIdleTime 空闲驱逐时长
     */
    public JschConnectionPool(String host, int port, String username, String password, Integer maxTotal,
                              Integer minIdle, Integer maxIdle, Duration timeout, Duration minEvictableIdleTime) {
        this(legacyProperties(host, port, username, password, maxTotal, minIdle, maxIdle, timeout, minEvictableIdleTime));
    }

    /**
     * 保留原配置类型的动态建池入口。
     *
     * @param poolKey 非空名称
     * @param properties 原配置类型
     */
    public void buildPool(String poolKey, SftpInfoProperties properties) {
        buildPool(poolKey, (SftpProperties) properties);
    }

    /**
     * 幂等注册命名池，同名已存在时不替换或关闭原池。
     *
     * @param poolKey 非空池名称
     * @param properties 完整安全配置
     */
    public void buildPool(String poolKey, SftpProperties properties) {
        Assert.hasText(poolKey, "SFTP池名称不能为空");
        Assert.state(!closed, "SFTP池管理器已关闭");
        if (pools.containsKey(poolKey)) {
            return;
        }
        GenericObjectPool<ChannelSftp> created = getPool(properties);
        boolean registered = false;
        try {
            lifecycle.lock();
            try {
                Assert.state(!closed, "SFTP池管理器已关闭");
                if (pools.containsKey(poolKey)) {
                    return;
                }
                Assert.state(pools.size() < maxPools, "SFTP命名池容量已满");
                pools.put(poolKey, created);
                registered = true;
            } finally {
                lifecycle.unlock();
            }
        } finally {
            if (!registered) {
                created.close();
            }
        }
    }

    /**
     * 保留原底层池访问能力；直接借还必须在返回的同一池实例上配对，不能与管理器借还混用。
     *
     * @param poolKey 池名称
     * @return 对应池，不存在时为 null
     */
    public GenericObjectPool<ChannelSftp> getPool(String poolKey) {
        return pools.get(poolKey);
    }

    /**
     * 创建未注册池，调用方必须自行 close。
     *
     * @param properties 原配置类型
     * @return 调用方拥有的池
     */
    public GenericObjectPool<ChannelSftp> getPool(SftpInfoProperties properties) {
        return getPool((SftpProperties) properties);
    }

    /**
     * 创建未注册池，初始构造不连接远端。
     *
     * @param properties 配置
     * @return 调用方拥有的池
     */
    public GenericObjectPool<ChannelSftp> getPool(SftpProperties properties) {
        JschFactory factory = new JschFactory(properties);
        GenericObjectPoolConfig<ChannelSftp> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(properties.getMaxTotal());
        config.setMaxIdle(Math.min(properties.getMaxIdle(), properties.getMaxTotal()));
        config.setMinIdle(properties.getMinIdle());
        config.setMaxWait(properties.getMaxWait());
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestOnCreate(true);
        config.setTestWhileIdle(true);
        config.setBlockWhenExhausted(true);
        config.setMinEvictableIdleDuration(properties.getMinEvictableIdleTime());
        config.setTimeBetweenEvictionRuns(properties.getEvictionInterval());
        return new GenericObjectPool<>(factory, config);
    }

    /**
     * 保留原直接参数建池入口，返回的池不自动注册。
     *
     * @param host 主机
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @param maxTotal 最大连接数
     * @param minIdle 最小空闲数
     * @param maxIdle 最大空闲数
     * @param timeout 原连接及借用等待时长
     * @param minEvictableIdleTime 空闲驱逐时长
     * @return 调用方拥有的池
     */
    public GenericObjectPool<ChannelSftp> buildJschConnectionPool(String host, int port, String username, String password,
                                                                 Integer maxTotal, Integer minIdle, Integer maxIdle,
                                                                 Duration timeout, Duration minEvictableIdleTime) {
        return getPool(legacyProperties(host, port, username, password, maxTotal, minIdle, maxIdle, timeout, minEvictableIdleTime));
    }

    /**
     * 原无主机信任参数入口统一使用标准 known_hosts，不引入不校验模式。
     *
     * @param host 主机
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @param maxTotal 最大连接数
     * @param minIdle 最小空闲数
     * @param maxIdle 最大空闲数
     * @param timeout 连接及等待时长
     * @param idle 空闲驱逐时长
     * @return 安全配置
     */
    private static SftpProperties legacyProperties(String host, int port, String username, String password,
                                                   Integer maxTotal, Integer minIdle, Integer maxIdle,
                                                   Duration timeout, Duration idle) {
        return SftpProperties.builder().host(host).port(port).username(username).password(password)
                .maxTotal(maxTotal).minIdle(minIdle).maxIdle(maxIdle).connectTimeout(timeout).maxWait(timeout)
                .minEvictableIdleTime(idle).knownHosts(JschFactory.defaultKnownHosts()).build();
    }

    /**
     * 借出默认通道，调用方使用后必须归还，不允许并发使用同一通道。
     *
     * @return 独占通道
     * @throws Exception 建连、认证、超时或中断
     */
    public ChannelSftp borrowObject() throws Exception {
        return borrowObject(DEFAULT_KEY);
    }

    /**
     * 按名称借用，保留中断状态，不在管理器锁内等待外部资源。
     *
     * @param poolKey 池名称
     * @return 独占通道
     * @throws Exception 借用失败或中断
     */
    public ChannelSftp borrowObject(String poolKey) throws Exception {
        Assert.state(!closed, "SFTP池管理器已关闭");
        GenericObjectPool<ChannelSftp> pool = pools.get(poolKey);
        Assert.notNull(pool, "sftp pool is null");
        try {
            ChannelSftp channel = pool.borrowObject();
            borrowed.put(channel, new Borrowed(poolKey, pool));
            return channel;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    /**
     * 归还默认通道，null 无操作。
     *
     * @param channel 借出的通道
     */
    public void returnObject(ChannelSftp channel) {
        returnObject(DEFAULT_KEY, channel);
    }

    /**
     * 归还到实际借出池，即使同名注册项已经关闭或重建也不会归还到新池。
     *
     * @param poolKey 原池名称
     * @param channel 借出的通道，null 无操作
     */
    public void returnObject(String poolKey, ChannelSftp channel) {
        release(poolKey, channel, false);
    }

    /**
     * 销毁操作失败的通道，避免断线对象占据活动计数。
     *
     * @param poolKey 原池名称
     * @param channel 借出的通道
     */
    public void invalidateObject(String poolKey, ChannelSftp channel) {
        release(poolKey, channel, true);
    }

    /**
     * 校验归属后原子移除借用记录，仅成功移除者可归还或销毁。
     *
     * @param poolKey 原池名称
     * @param channel 通道
     * @param invalidate 是否强制销毁
     */
    private void release(String poolKey, ChannelSftp channel, boolean invalidate) {
        if (channel == null) {
            return;
        }
        Borrowed owner = borrowed.get(channel);
        Assert.state(owner != null, "通道不是通过当前管理器借出或已经归还");
        Assert.isTrue(owner.name().equals(poolKey), "通道不能归还到其他命名池");
        Assert.state(borrowed.remove(channel, owner), "通道已经被归还");
        try {
            if (invalidate || !channel.isConnected()) {
                owner.pool().invalidateObject(channel);
            } else {
                owner.pool().returnObject(channel);
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("SFTP通道归还或销毁失败", exception);
        }
    }

    /**
     * 返回只读名称快照，不暴露可修改注册表。
     *
     * @return 当前名称集合
     */
    public Set<String> names() {
        return Set.copyOf(pools.keySet());
    }

    /**
     * 关闭指定池并移除注册项，已有借用归还时由原池销毁，名称可重新创建。
     *
     * @param poolKey 池名称
     */
    public void close(String poolKey) {
        GenericObjectPool<ChannelSftp> pool;
        lifecycle.lock();
        try {
            pool = pools.remove(poolKey);
        } finally {
            lifecycle.unlock();
        }
        Assert.notNull(pool, "sftp pool is null");
        pool.close();
    }

    /**
     * 关闭整个管理器的全部命名池，不只关闭默认池；在途借用仍可通过原归属归还。
     */
    @Override
    public void close() {
        List<GenericObjectPool<ChannelSftp>> closing;
        lifecycle.lock();
        try {
            closed = true;
            closing = new ArrayList<>(pools.values());
            pools.clear();
        } finally {
            lifecycle.unlock();
        }
        RuntimeException failure = null;
        for (GenericObjectPool<ChannelSftp> pool : closing) {
            try {
                pool.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * 借用归属(Borrowed)绑定实际池实例，避免关闭重建后的跨池归还。
     *
     * @author linshiqiang
     * @since 2026-10-06 02:50:50
     * @param name 原名称
     * @param pool 原池实例
     */
    private record Borrowed(
            /**
             * 借用时的名称。
             */
            String name,

            /**
             * 借出此通道的池。
             */
            GenericObjectPool<ChannelSftp> pool) {
    }
}
