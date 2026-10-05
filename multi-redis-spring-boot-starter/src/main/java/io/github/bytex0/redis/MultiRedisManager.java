package io.github.bytex0.redis;

import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 多Redis管理(MultiRedisManager)显式路由、配置预校验及客户端生命周期
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
public class MultiRedisManager implements AutoCloseable {

    /**
     * 本管理器拥有的客户端
     */
    private final Map<String, RedissonClient> clients;

    /**
     * 默认客户端名称
     */
    private final String primary;

    /**
     * 是否已经关闭
     */
    private volatile boolean closed;

    public MultiRedisManager(MultiRedisProperties properties, RedisClientFactory factory) {
        Assert.notEmpty(properties.getClients(), "multi-redis.clients不能为空");
        Assert.isTrue(properties.getClients().size() <= 16, "最多配置16个Redis客户端");
        Map<String, Config> configs = new LinkedHashMap<>();
        properties.getClients().forEach((name, connection) -> {
            Assert.hasText(name, "Redis客户端名称不能为空");
            Assert.notNull(connection, "Redis连接配置不能为空");
            if (connection.isEnabled()) { configs.put(name, config(connection)); }
        });
        this.primary = properties.getPrimary();
        Assert.isTrue(configs.containsKey(primary), "默认Redis客户端不存在或未启用");
        Map<String, RedissonClient> opened = new LinkedHashMap<>();
        try {
            configs.forEach((name, config) -> {
                RedissonClient client = factory.create(config);
                Assert.notNull(client, "Redis客户端工厂不能返回null");
                opened.put(name, client);
            });
        } catch (RuntimeException exception) {
            opened.values().forEach(client -> {
                try { client.shutdown(); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            });
            throw exception;
        }
        clients = Map.copyOf(opened);
    }

    public RedissonClient get(String name) {
        Assert.state(!closed, "Redis管理器已关闭");
        RedissonClient client = clients.get(name);
        Assert.notNull(client, "Redis客户端名称不存在");
        return client;
    }

    public RedissonClient primary() { return get(primary); }

    public Set<String> names() { return clients.keySet(); }

    @Override
    public synchronized void close() {
        if (closed) { return; }
        closed = true;
        RuntimeException failure = null;
        for (RedissonClient client : clients.values()) {
            try { client.shutdown(); } catch (RuntimeException exception) {
                if (failure == null) { failure = exception; } else { failure.addSuppressed(exception); }
            }
        }
        if (failure != null) { throw failure; }
    }

    private Config config(MultiRedisProperties.Connection connection) {
        Assert.isTrue(connection.getConnectTimeout() > 0 && connection.getTimeout() > 0
                && connection.getPoolSize() > 0 && connection.getNettyThreads() > 0, "Redis超时和资源参数必须大于0");
        Assert.isTrue(connection.getDatabase() >= 0, "Redis数据库编号不能为负数");
        Config config = new Config().setTransportMode(TransportMode.NIO)
                .setNettyThreads(connection.getNettyThreads()).setThreads(2);
        config.setCodec(switch (connection.getCodec().toUpperCase(Locale.ROOT)) {
            case "STRING" -> StringCodec.INSTANCE;
            case "JSON" -> new TypedJsonJackson3Codec(Object.class);
            default -> throw new IllegalArgumentException("Redis codec仅支持STRING或JSON");
        });
        switch (connection.getMode().toUpperCase(Locale.ROOT)) {
            case "SINGLE" -> config.useSingleServer().setAddress(address(connection.getAddress()))
                    .setDatabase(connection.getDatabase()).setUsername(connection.getUsername()).setPassword(connection.getPassword())
                    .setConnectTimeout(connection.getConnectTimeout()).setTimeout(connection.getTimeout())
                    .setConnectionMinimumIdleSize(1).setConnectionPoolSize(connection.getPoolSize());
            case "CLUSTER" -> {
                Assert.isTrue(connection.getDatabase() == 0, "Redis集群不支持选择逻辑库");
                Assert.notEmpty(connection.getNodes(), "Redis集群节点不能为空");
                config.useClusterServers().addNodeAddress(connection.getNodes().stream().map(this::address).toArray(String[]::new))
                        .setUsername(connection.getUsername()).setPassword(connection.getPassword())
                        .setConnectTimeout(connection.getConnectTimeout()).setTimeout(connection.getTimeout())
                        .setMasterConnectionMinimumIdleSize(1).setMasterConnectionPoolSize(connection.getPoolSize())
                        .setSlaveConnectionMinimumIdleSize(1).setSlaveConnectionPoolSize(connection.getPoolSize());
            }
            default -> throw new IllegalArgumentException("Redis模式仅支持SINGLE或CLUSTER");
        }
        return config;
    }

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
