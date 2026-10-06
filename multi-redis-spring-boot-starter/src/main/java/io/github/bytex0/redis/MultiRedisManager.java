package io.github.bytex0.redis;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多Redis管理(MultiRedisManager)显式路由、配置预校验及客户端生命周期
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
public class MultiRedisManager implements AutoCloseable {

    /**
     * 单个应用最多注册的客户端数量，防止配置错误耗尽连接和线程。
     */
    private static final int MAX_CLIENTS = 16;

    /**
     * 本管理器拥有的客户端
     */
    private final Map<String, RedissonClient> clients;

    /**
     * 默认客户端名称
     */
    private final String primary;

    /**
     * 可选机房别名到名称的只读路由表。
     */
    private final Map<String, String> locations;

    /**
     * 是否已经关闭
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 先验证全部配置，再创建归本管理器所有的客户端；失败时清理已创建实例。
     *
     * @param properties 命名连接配置
     * @param factory 客户端工厂，返回值的所有权交给本管理器
     */
    public MultiRedisManager(MultiRedisProperties properties, RedisClientFactory factory) {
        Assert.notNull(properties, "Redis配置不能为空");
        Assert.notNull(factory, "Redis客户端工厂不能为空");
        Assert.notEmpty(properties.getClients(), "multi-redis.clients不能为空");
        Assert.isTrue(properties.getClients().size() <= MAX_CLIENTS, "最多配置16个Redis客户端");
        Map<String, Config> configs = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        properties.getClients().forEach((name, connection) -> {
            Assert.hasText(name, "Redis客户端名称不能为空");
            Assert.notNull(connection, "Redis连接配置不能为空");
            if (connection.isEnabled()) {
                configs.put(name, config(connection));
                String location = connection.getLocation();
                if (location != null && !location.isBlank()) {
                    Assert.isTrue(!properties.getClients().containsKey(location) || name.equals(location),
                            "机房别名不能覆盖另一个客户端名称");
                    Assert.isTrue(aliases.putIfAbsent(location, name) == null, "Redis机房别名不能重复");
                }
            }
        });
        this.locations = Map.copyOf(aliases);
        this.primary = properties.getPrimary();
        Assert.isTrue(configs.containsKey(primary), "默认Redis客户端不存在或未启用");
        if (properties.getBackup() != null) {
            Assert.hasText(properties.getBackup(), "备客户端名称不能为空");
            String backup = aliases.getOrDefault(properties.getBackup(), properties.getBackup());
            Assert.isTrue(configs.containsKey(backup) && !backup.equals(primary), "备客户端必须存在且不同于主客户端");
        }
        Assert.isTrue(properties.getReplicationQueueCapacity() > 0, "复制容量必须大于0");
        Assert.notNull(properties.getReplicationTimeout(), "复制等待上限不能为空");
        Assert.isTrue(properties.getReplicationTimeout().toMillis() > 0, "复制等待上限至少一毫秒");
        Map<String, RedissonClient> opened = new LinkedHashMap<>();
        try {
            configs.forEach((name, config) -> {
                RedissonClient client = factory.create(config);
                Assert.notNull(client, "Redis客户端工厂不能返回null");
                opened.put(name, client);
            });
        } catch (RuntimeException exception) {
            uniqueClients(opened).forEach(client -> {
                try {
                    client.shutdown();
                } catch (RuntimeException cleanup) {
                    if (cleanup != exception) {
                        exception.addSuppressed(cleanup);
                    }
                }
            });
            throw exception;
        }
        clients = Map.copyOf(opened);
    }

    /**
     * 获取命名客户端，不改变路由、不转移所有权；不得与容器销毁并行发起新操作。
     *
     * @param name 配置的名称
     * @return 共享客户端，调用方不得 shutdown
     */
    public RedissonClient get(String name) {
        Assert.state(!closed.get(), "Redis管理器已关闭");
        Assert.hasText(name, "Redis客户端名称不能为空");
        RedissonClient client = clients.get(locations.getOrDefault(name, name));
        Assert.notNull(client, "Redis客户端名称不存在");
        return client;
    }

    /**
     * 获取默认客户端。
     *
     * @return 默认共享客户端
     */
    public RedissonClient primary() {
        return get(primary);
    }

    /**
     * 获取不可修改的名称集合。
     *
     * @return 配置中启用的名称
     */
    public Set<String> names() {
        return clients.keySet();
    }

    /**
     * 原子开始关闭并释放全部不同实例。并发或重入调用立即返回，不等待另一关闭线程。
     * 不持有锁调用 SDK；即使某个实例关闭失败，也继续清理其他实例并抛出汇总异常。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException failure = null;
        for (RedissonClient client : uniqueClients(clients)) {
            try {
                client.shutdown();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * 按对象身份去重，不依赖用户客户端实现的 equals/hashCode。
     *
     * @param opened 已创建的命名客户端
     * @return 需要分别释放的实例
     */
    private static Set<RedissonClient> uniqueClients(Map<String, RedissonClient> opened) {
        Set<RedissonClient> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(opened.values());
        return result;
    }

    /**
     * 构建并验证单个连接，尚不访问外部服务。
     *
     * @param connection 连接参数
     * @return SDK 配置
     */
    private Config config(MultiRedisProperties.Connection connection) {
        Assert.isTrue(connection.getConnectTimeout() > 0 && connection.getTimeout() > 0
                && connection.getPoolSize() > 0 && connection.getNettyThreads() > 0, "Redis超时和资源参数必须大于0");
        Assert.isTrue(connection.getDatabase() >= 0, "Redis数据库编号不能为负数");
        Assert.hasText(connection.getMode(), "Redis模式不能为空");
        Assert.notNull(connection.getValueType(), "Redis值类型不能为空");
        Assert.isTrue(connection.getIdleConnectionTimeout() > 0 && connection.getRetryAttempts() >= 0
                && connection.getRetryInterval() > 0 && connection.getSlavePoolSize() > 0
                && connection.getScanInterval() > 0 && connection.getSlavesSyncTimeout() > 0,
                "Redis连接资源和重试参数不合法");
        Assert.notNull(connection.getReadMode(), "Redis读取模式不能为空");
        Config config = new Config().setTransportMode(TransportMode.NIO)
                .setNettyThreads(connection.getNettyThreads()).setThreads(2)
                .setUsername(connection.getUsername()).setPassword(connection.getPassword())
                .setCheckLockSyncedSlaves(connection.isCheckLockSyncedSlaves())
                .setSlavesSyncTimeout(connection.getSlavesSyncTimeout())
                .setCodec(RedisConfigurationSupport.codec(connection));
        switch (connection.getMode().toUpperCase(Locale.ROOT)) {
            case "SINGLE" -> config.useSingleServer().setAddress(address(connection.getAddress()))
                    .setDatabase(connection.getDatabase())
                    .setConnectTimeout(connection.getConnectTimeout()).setTimeout(connection.getTimeout())
                    .setIdleConnectionTimeout(connection.getIdleConnectionTimeout())
                    .setRetryAttempts(connection.getRetryAttempts()).setRetryInterval(connection.getRetryInterval())
                    .setConnectionMinimumIdleSize(1).setConnectionPoolSize(connection.getPoolSize());
            case "CLUSTER" -> {
                Assert.isTrue(connection.getDatabase() == 0, "Redis集群不支持选择逻辑库");
                Assert.notEmpty(connection.getNodes(), "Redis集群节点不能为空");
                config.useClusterServers().addNodeAddress(connection.getNodes().stream().map(this::address).toArray(String[]::new))
                        .setConnectTimeout(connection.getConnectTimeout()).setTimeout(connection.getTimeout())
                        .setIdleConnectionTimeout(connection.getIdleConnectionTimeout())
                        .setRetryAttempts(connection.getRetryAttempts()).setRetryInterval(connection.getRetryInterval())
                        .setScanInterval(connection.getScanInterval()).setReadMode(connection.getReadMode())
                        .setCheckSlotsCoverage(connection.isCheckSlotsCoverage())
                        .setMasterConnectionMinimumIdleSize(1).setMasterConnectionPoolSize(connection.getPoolSize())
                        .setSlaveConnectionMinimumIdleSize(1).setSlaveConnectionPoolSize(connection.getSlavePoolSize());
            }
            default -> throw new IllegalArgumentException("Redis模式仅支持SINGLE或CLUSTER");
        }
        return config;
    }

    /**
     * 规范化地址并禁止在地址里携带凭据或路径。
     *
     * @param address 原始 host:port 或 redis(s) 地址
     * @return 规范化地址
     */
    private String address(String address) {
        Assert.hasText(address, "Redis地址不能为空");
        String normalized = address.contains("://") ? address : "redis://" + address;
        URI uri = URI.create(normalized);
        Assert.isTrue(("redis".equals(uri.getScheme()) || "rediss".equals(uri.getScheme()))
                && uri.getHost() != null && uri.getPort() > 0 && uri.getPort() <= 65535
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && uri.getPath().isEmpty(), "Redis地址必须是无凭据和路径的redis(s)://host:port");
        return normalized;
    }
}
