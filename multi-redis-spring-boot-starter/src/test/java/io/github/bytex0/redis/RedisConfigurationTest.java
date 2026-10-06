package io.github.bytex0.redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import com.google.protobuf.StringValue;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 兼容配置测试(RedisConfigurationTest)验证旧前缀、用户覆盖和所有实际支持的序列化分支。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:07:28
 */
class RedisConfigurationTest {

    /**
     * 旧三集群配置绑定所有生效参数，保留 Bean 名与机房位置路由。
     */
    @Test
    void shouldBindLegacyClustersAndLocations() {
        List<Config> configs = new ArrayList<>();
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MultiRedissonConfig.class))
                .withBean(RedisClientFactory.class, () -> config -> {
                    configs.add(config);
                    return mock(RedissonClient.class);
                })
                .withPropertyValues("multi-redis.enabled=true",
                        "spring.data.redis.cluster.nodes[0]=localhost:7000",
                        "spring.data.redis.cluster.location=A",
                        "spring.data.redis.cluster2.active=true",
                        "spring.data.redis.cluster2.nodes[0]=localhost:7001",
                        "spring.data.redis.cluster2.location=B",
                        "spring.data.redis.cluster3.active=true",
                        "spring.data.redis.cluster3.nodes[0]=localhost:7002",
                        "spring.data.redis.cluster3.location=C",
                        "spring.data.redis.connection-timeout=2100", "spring.data.redis.response-timeout=2200",
                        "spring.data.redis.idle-connection-timeout=2300",
                        "spring.data.redis.master-connection-pool-size=7", "spring.data.redis.slave-connection-pool-size=9",
                        "spring.data.redis.retry-attempts=2", "spring.data.redis.retry-interval=200",
                        "spring.data.redis.read-mode=MASTER_SLAVE", "spring.data.redis.scan-interval=300",
                        "spring.data.redis.check-slots-coverage=false",
                        "spring.data.redis.check-lock-synced-slaves=true", "spring.data.redis.slaves-sync-timeout=400")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RedissonUtil.class);
                    MultiRedisManager manager = context.getBean(MultiRedisManager.class);
                    assertThat(context.getBean("redissonClient")).isSameAs(manager.get("A"));
                    assertThat(context.getBean("redissonClient2")).isSameAs(manager.get("B"));
                    assertThat(context.getBean("redissonClient3")).isSameAs(manager.get("C"));
                    assertThat(context.getBean(RedissonUtil.class).getBackRedissonClient()).isSameAs(manager.get("B"));
                    assertThat(context.getBean(RedissonUtil.class).getRedissonClient("C")).isSameAs(manager.get("C"));
                    assertThat(context.getBean(MultiRedisProperties.class).getResponseTimeout()).isEqualTo(2200);
                    Config config = configs.getFirst();
                    assertThat(config.useClusterServers().getTimeout()).isEqualTo(2200);
                    assertThat(config.useClusterServers().getConnectTimeout()).isEqualTo(2100);
                    assertThat(config.useClusterServers().getIdleConnectionTimeout()).isEqualTo(2300);
                    assertThat(config.useClusterServers().getMasterConnectionPoolSize()).isEqualTo(7);
                    assertThat(config.useClusterServers().getSlaveConnectionPoolSize()).isEqualTo(9);
                    assertThat(config.useClusterServers().getRetryAttempts()).isEqualTo(2);
                    assertThat(config.useClusterServers().getReadMode()).isEqualTo(ReadMode.MASTER_SLAVE);
                    assertThat(config.useClusterServers().getScanInterval()).isEqualTo(300);
                    assertThat(config.useClusterServers().isCheckSlotsCoverage()).isFalse();
                    assertThat(config.isCheckLockSyncedSlaves()).isTrue();
                    assertThat(config.getSlavesSyncTimeout()).isEqualTo(400);
                    assertThat(context.getBean("otherRoomExecutor")).isInstanceOf(ExecutorService.class);
                });
    }

    /**
     * 用户提供客户端时整个默认连接基础设施退让，不打开无用的第二组连接。
     */
    @Test
    void shouldBackOffToUserClientWithoutCreatingConnections() {
        RedissonClient supplied = mock(RedissonClient.class);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MultiRedissonConfig.class))
                .withPropertyValues("multi-redis.enabled=true")
                .withBean(RedissonClient.class, () -> supplied)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RedissonClient.class)
                        .doesNotHaveBean(MultiRedisManager.class));
    }

    /**
     * 用户可单独覆盖工具，不影响管理器的正常注册。
     */
    @Test
    void shouldBackOffToUserTool() {
        RedissonUtil supplied = mock(RedissonUtil.class);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MultiRedissonConfig.class))
                .withPropertyValues("multi-redis.enabled=true", "multi-redis.clients.main.address=localhost:6379")
                .withBean(RedisClientFactory.class, () -> config -> mock(RedissonClient.class))
                .withBean(RedissonUtil.class, () -> supplied)
                .run(context -> assertThat(context.getBean(RedissonUtil.class)).isSameAs(supplied));
    }

    /**
     * 命名连接完整覆盖旧配置时，旧 active 开关不能触发不存在的客户端别名。
     */
    @Test
    void shouldPreferNamedConnectionsOverLegacyFlags() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MultiRedissonConfig.class))
                .withPropertyValues("multi-redis.enabled=true", "multi-redis.clients.main.address=localhost:6379",
                        "spring.data.redis.cluster2.active=true", "spring.data.redis.cluster3.active=true")
                .withBean(RedisClientFactory.class, () -> config -> mock(RedissonClient.class))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RedissonClient.class)
                        .doesNotHaveBean("redissonClient2").doesNotHaveBean("redissonClient3"));
    }

    /**
     * 所有编码器都完成实际编解码，未知类型不静默改变线格式。
     *
     * @throws Exception 编解码失败时抛出
     */
    @Test
    void shouldRoundTripAllSupportedCodecs() throws Exception {
        for (String name : List.of("STRING", "JSON", "KRYO", "KRYO5", "PROTOBUF")) {
            Codec codec = codec(name, String.class);
            ByteBuf data = codec.getValueEncoder().encode("中文-value");
            try {
                assertThat(codec.getValueDecoder().decode(data, null)).isEqualTo("中文-value");
            } finally {
                data.release();
            }
        }
        assertThatThrownBy(() -> codec("FST", String.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec("PROTOBUF", Object.class)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 显式类型的 JSON、Kryo 与 Protobuf 可以还原模型，不要求业务自己写编码器。
     *
     * @throws Exception 编解码失败时抛出
     */
    @Test
    void shouldRoundTripDeclaredModels() throws Exception {
        Value value = new Value();
        value.setName("typed-value");
        for (String name : List.of("JSON", "KRYO", "KRYO5", "PROTOBUF")) {
            Codec codec = codec(name, Value.class);
            ByteBuf data = codec.getValueEncoder().encode(value);
            try {
                assertThat(codec.getValueDecoder().decode(data, null)).isEqualTo(value);
            } finally {
                data.release();
            }
            ByteBuf field = codec.getMapValueEncoder().encode(value);
            try {
                assertThat(codec.getMapValueDecoder().decode(field, null)).isEqualTo(value);
            } finally {
                field.release();
            }
        }
    }

    /**
     * Google 生成消息保持原 Protobuf 二进制格式，并使用标准解析器还原。
     *
     * @throws Exception 编解码失败时抛出
     */
    @Test
    void shouldPreserveGeneratedProtobufWireFormat() throws Exception {
        StringValue value = StringValue.of("protobuf");
        Codec codec = codec("PROTOBUF", StringValue.class);
        ByteBuf data = codec.getValueEncoder().encode(value);
        try {
            assertThat(ByteBufUtil.getBytes(data)).containsExactly(value.toByteArray());
            assertThat(codec.getValueDecoder().decode(data, null)).isEqualTo(value);
        } finally {
            data.release();
        }
    }

    /**
     * 不使用 Protobuf 时无需安装其可选依赖。
     */
    @Test
    void shouldStartWithoutOptionalProtobufDependencies() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MultiRedissonConfig.class))
                .withClassLoader(new FilteredClassLoader("io.protostuff", "com.google.protobuf"))
                .withPropertyValues("multi-redis.enabled=true", "multi-redis.clients.main.address=localhost:6379")
                .withBean(RedisClientFactory.class, () -> config -> mock(RedissonClient.class))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RedissonUtil.class));
    }

    /**
     * 创建管理器实际采用的编码器，不通过私有方法直接测试实现细节。
     *
     * @param name 编码名称
     * @param type 目标类型
     * @return 管理器向工厂交付的编码器
     */
    private Codec codec(String name, Class<?> type) {
        MultiRedisProperties properties = new MultiRedisProperties();
        MultiRedisProperties.Connection connection = new MultiRedisProperties.Connection();
        connection.setAddress("localhost:6379");
        connection.setCodec(name);
        connection.setValueType(type);
        connection.setAllowedTypes(List.of(type));
        properties.getClients().put("main", connection);
        List<Config> configurations = new ArrayList<>();
        try (MultiRedisManager manager = new MultiRedisManager(properties, config -> {
            configurations.add(config);
            return mock(RedissonClient.class);
        })) {
            assertThat(manager.names()).containsExactly("main");
            return configurations.getFirst().getCodec();
        }
    }

    /**
     * 编码模型(Value)用于验证显式注册的可还原对象。
     *
     * @author linshiqiang
     * @since 2026-10-06 10:07:28
     */
    @Data
    public static class Value {

        /**
         * 模型名称，可为 null，默认无值。
         */
        private String name;
    }
}
