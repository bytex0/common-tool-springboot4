package io.github.bytex0.redis;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.Kryo5Codec;
import org.redisson.codec.KryoCodec;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 配置适配(RedisConfigurationSupport)将旧前缀和编解码选择转换为命名连接。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:58:09
 */
final class RedisConfigurationSupport {

    /**
     * 仅提供配置转换方法，不持有全局状态。
     */
    private RedisConfigurationSupport() {
    }

    /**
     * 在没有命名连接时读取旧配置；命名连接存在时不会混用旧连接参数。
     *
     * @param properties 当前配置，转换结果写回此实例
     * @param environment 配置环境
     */
    static void resolve(MultiRedisProperties properties, Environment environment) {
        if (!properties.getClients().isEmpty()) {
            return;
        }
        LegacyRedisOptions source = properties;
        if (properties.getCluster().getNodes().isEmpty()) {
            source = Binder.get(environment).bind("spring.data.redis", LegacyRedisOptions.class)
                    .orElse(properties);
        }
        if (source != properties) {
            BeanUtils.copyProperties(source, properties);
        }
        Assert.notNull(source.getCluster(), "默认集群配置不能为空");
        Map<String, MultiRedisProperties.Connection> clients = new LinkedHashMap<>();
        clients.put(properties.getPrimary(), connection(source, source.getCluster()));
        if (Boolean.TRUE.equals(source.getCluster2().getActive())) {
            clients.put("secondary", connection(source, source.getCluster2()));
            if (!StringUtils.hasText(properties.getBackup())) {
                properties.setBackup("secondary");
            }
        }
        if (Boolean.TRUE.equals(source.getCluster3().getActive())) {
            clients.put("third", connection(source, source.getCluster3()));
        }
        properties.setClients(clients);
    }

    /**
     * 转换实际生效过的旧连接参数，保留密码覆盖和默认值。
     *
     * @param source 公共参数
     * @param cluster 指定集群参数
     * @return 命名连接参数
     */
    private static MultiRedisProperties.Connection connection(LegacyRedisOptions source,
                                                              LegacyRedisOptions.Cluster cluster) {
        Assert.notNull(source.getConnectionTimeout(), "连接超时不能为空");
        Assert.notNull(source.getMasterConnectionPoolSize(), "主节点连接池大小不能为空");
        MultiRedisProperties.Connection result = new MultiRedisProperties.Connection();
        result.setMode("CLUSTER");
        result.setNodes(cluster.getNodes());
        result.setLocation(cluster.getLocation());
        result.setPassword(StringUtils.hasText(cluster.getPassword()) ? cluster.getPassword() : source.getPassword());
        result.setNettyThreads(cluster.getNettyThreads());
        result.setCodec(source.getCodecType());
        result.setConnectTimeout(source.getConnectionTimeout());
        result.setTimeout(source.getResponseTimeout());
        result.setIdleConnectionTimeout(source.getIdleConnectionTimeout());
        result.setPoolSize(source.getMasterConnectionPoolSize());
        result.setSlavePoolSize(source.getSlaveConnectionPoolSize());
        result.setRetryAttempts(source.getRetryAttempts());
        result.setRetryInterval(source.getRetryInterval());
        result.setScanInterval(source.getScanInterval());
        result.setReadMode(source.getReadMode());
        result.setCheckSlotsCoverage(source.isCheckSlotsCoverage());
        result.setCheckLockSyncedSlaves(source.isCheckLockSyncedSlaves());
        result.setSlavesSyncTimeout(source.getSlavesSyncTimeout());
        return result;
    }

    /**
     * 选择可用编码器；JSON 不启用动态类型，二进制模型使用显式类型边界。
     *
     * @param connection 连接及编码选项
     * @return 编解码器，失败时不会静默切换协议
     */
    static Codec codec(MultiRedisProperties.Connection connection) {
        String name = connection.getCodec() == null ? "STRING" : connection.getCodec().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "STRING" -> StringCodec.INSTANCE;
            case "JSON" -> new TypedJsonJackson3Codec(connection.getValueType(), Object.class, connection.getValueType());
            case "KRYO" -> new KryoCodec(allowedTypes(connection));
            case "KRYO5" -> new Kryo5Codec(allowedTypes(connection).stream()
                    .map(Class::getName).collect(Collectors.toCollection(LinkedHashSet::new)), false);
            case "PROTOBUF" -> {
                Assert.isTrue(connection.getValueType() != null && connection.getValueType() != Object.class,
                        "PROTOBUF必须设置具体value-type");
                Assert.isTrue(ClassUtils.isPresent("io.protostuff.runtime.RuntimeSchema",
                        RedisConfigurationSupport.class.getClassLoader())
                        && ClassUtils.isPresent("com.google.protobuf.MessageLite",
                        RedisConfigurationSupport.class.getClassLoader()),
                        "PROTOBUF需要protostuff-core、protostuff-runtime和protobuf-java依赖");
                yield new ProtobufJackson3Codec<>(connection.getValueType());
            }
            default -> throw new IllegalArgumentException("Redis codec不支持所选类型");
        };
    }

    /**
     * 构造稳定的二进制类型注册列表；额外业务类按全名排序，避免集合遍历影响注册编号。
     *
     * @param connection 类型选项
     * @return 非空、去重的允许类型
     */
    private static List<Class<?>> allowedTypes(MultiRedisProperties.Connection connection) {
        Set<Class<?>> types = new LinkedHashSet<>(List.of(String.class, byte[].class, Integer.class,
                Long.class, Double.class, Boolean.class));
        Assert.notNull(connection.getAllowedTypes(), "allowed-types不能为空");
        connection.getAllowedTypes().stream().sorted(Comparator.comparing(Class::getName))
                .forEach(types::add);
        return new ArrayList<>(types);
    }
}
