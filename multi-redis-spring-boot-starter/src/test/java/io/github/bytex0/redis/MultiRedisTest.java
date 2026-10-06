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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 多Redis(MultiRedisTest)路由配置、失败清理和安全JSON测试
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
class MultiRedisTest {

    /**
     * 默认不开连接，显式开启后由管理器且仅由管理器释放默认客户端。
     */
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

    /**
     * 任一配置非法时不能已经创建其他连接。
     */
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

    /**
     * 工厂返回同一实例时，多个路由仍只能释放一次该资源。
     */
    @Test
    void shouldCloseSharedClientOnlyOnce() {
        MultiRedisProperties properties = properties();
        properties.getClients().put("second", properties.getClients().get("main"));
        RedissonClient client = mock(RedissonClient.class);
        try (MultiRedisManager ignored = new MultiRedisManager(properties, config -> client)) {
            assertThat(ignored.names()).hasSize(2);
        }
        verify(client, times(1)).shutdown();
    }

    /**
     * SDK 关闭回调中另一线程重入 close 不能被管理器持锁阻塞。
     *
     * @throws Exception 等待异步回调失败时抛出
     */
    @Test
    void shouldNotHoldMonitorWhileShuttingDown() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        MultiRedisManager manager = new MultiRedisManager(properties(), config -> client);
        CountDownLatch returned = new CountDownLatch(1);
        doAnswer(invocation -> {
            CompletableFuture.runAsync(() -> {
                manager.close();
                returned.countDown();
            });
            assertThat(returned.await(1, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(client).shutdown();
        manager.close();
        verify(client, times(1)).shutdown();
    }

    /**
     * 后续工厂创建失败时释放前面已创建的实例。
     */
    @Test
    void shouldCleanAlreadyCreatedClientsWhenLaterCreationFails() {
        MultiRedisProperties properties = properties();
        MultiRedisProperties.Connection second = new MultiRedisProperties.Connection();
        second.setAddress("localhost:6380");
        properties.getClients().put("second", second);
        RedissonClient first = mock(RedissonClient.class);
        AtomicInteger count = new AtomicInteger();
        assertThatThrownBy(() -> new MultiRedisManager(properties, config -> {
            if (count.getAndIncrement() == 0) {
                return first;
            }
            throw new IllegalStateException("connection failed");
        })).isInstanceOf(IllegalStateException.class);
        verify(first).shutdown();
    }

    /**
     * 单机和集群配置同时生效，未知名称与关闭后的访问必须失败。
     */
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

    /**
     * 默认 JSON 不根据数据中的类名创建任意运行时类型。
     *
     * @throws Exception 编解码失败时抛出
     */
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

    /**
     * 创建不联网的最小连接参数。
     *
     * @return 测试配置
     */
    private MultiRedisProperties properties() {
        MultiRedisProperties properties = new MultiRedisProperties();
        MultiRedisProperties.Connection main = new MultiRedisProperties.Connection();
        main.setAddress("localhost:6379");
        properties.getClients().put("main", main);
        return properties;
    }
}
