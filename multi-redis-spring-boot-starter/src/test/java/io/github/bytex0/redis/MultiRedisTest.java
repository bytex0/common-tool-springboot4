package io.github.bytex0.redis;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 多Redis(MultiRedisTest)路由配置、失败清理和安全JSON测试
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
class MultiRedisTest {

    @Test
    void shouldConfigureOnlyWhenEnabledAndShutdownOnce() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MultiRedissonConfig.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(RedissonClient.class));
        RedissonClient client = mock(RedissonClient.class);
        runner.withPropertyValues("multi-redis.enabled=true", "multi-redis.clients.main.address=redis://127.0.0.1:1")
                .withBean(RedisClientFactory.class, () -> config -> client).run(context -> {
                    assertThat(context).hasSingleBean(MultiRedisManager.class).hasSingleBean(RedissonClient.class);
                    assertThat(context.getBean(MultiRedisManager.class).primary()).isSameAs(client);
                });
        verify(client, times(1)).shutdown();
    }

    @Test
    void shouldValidateAllConnectionsBeforeOpeningAny() {
        MultiRedisProperties properties = properties();
        MultiRedisProperties.Connection invalid = new MultiRedisProperties.Connection();
        invalid.setAddress("http://localhost:6379");
        properties.getClients().put("invalid", invalid);
        AtomicInteger created = new AtomicInteger();
        assertThatThrownBy(() -> new MultiRedisManager(properties, config -> {
            created.incrementAndGet();
            return mock(RedissonClient.class);
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(created).hasValue(0);
    }

    @Test
    void shouldCleanAlreadyCreatedClientsWhenLaterCreationFails() {
        MultiRedisProperties properties = properties();
        MultiRedisProperties.Connection second = new MultiRedisProperties.Connection();
        second.setAddress("localhost:6380");
        properties.getClients().put("second", second);
        RedissonClient first = mock(RedissonClient.class);
        AtomicInteger count = new AtomicInteger();
        assertThatThrownBy(() -> new MultiRedisManager(properties, config -> {
            if (count.getAndIncrement() == 0) { return first; }
            throw new IllegalStateException("connection failed");
        })).isInstanceOf(IllegalStateException.class);
        verify(first).shutdown();
    }

    @Test
    void shouldBuildSingleAndClusterConfigurationsAndRejectUnknownNames() {
        MultiRedisProperties properties = properties();
        MultiRedisProperties.Connection cluster = new MultiRedisProperties.Connection();
        cluster.setMode("CLUSTER");
        cluster.setNodes(List.of("localhost:7000", "redis://localhost:7001"));
        properties.getClients().put("cluster", cluster);
        List<Config> configs = new ArrayList<>();
        MultiRedisManager manager = new MultiRedisManager(properties, config -> {
            configs.add(config);
            return mock(RedissonClient.class);
        });
        assertThat(manager.names()).containsExactlyInAnyOrder("main", "cluster");
        assertThat(configs.getFirst().useSingleServer().getAddress()).isEqualTo("redis://localhost:6379");
        assertThat(configs.getLast().useClusterServers().getNodeAddresses()).hasSize(2);
        assertThatThrownBy(() -> manager.get("unknown")).isInstanceOf(IllegalArgumentException.class);
        manager.close();
        manager.close();
        assertThatThrownBy(manager::primary).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldDecodeJsonAsDataWithoutPolymorphicInstantiation() throws Exception {
        TypedJsonJackson3Codec codec = new TypedJsonJackson3Codec(Object.class);
        Map<String, Object> data = Map.of("@class", "java.lang.Runtime", "text", "中文", "count", 1);
        var buffer = codec.getValueEncoder().encode(data);
        try {
            assertThat(codec.getValueDecoder().decode(buffer, null)).isEqualTo(data);
        } finally {
            buffer.release();
        }
    }

    private MultiRedisProperties properties() {
        MultiRedisProperties properties = new MultiRedisProperties();
        MultiRedisProperties.Connection main = new MultiRedisProperties.Connection();
        main.setAddress("localhost:6379");
        properties.getClients().put("main", main);
        return properties;
    }
}
