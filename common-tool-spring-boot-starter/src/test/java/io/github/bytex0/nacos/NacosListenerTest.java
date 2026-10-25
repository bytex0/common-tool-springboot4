package io.github.bytex0.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nacos 初始更新顺序、真实标识、失败回滚、关闭竞争及配置优先级测试。
 *
 * @author bytex0
 * @since 2026-10-06 18:32:38
 */
class NacosListenerTest {

    /**
     * 受控 SDK 调用超时，单位毫秒。
     */
    private static final long REQUEST_TIMEOUT = 1000;

    /**
     * 验证多个清理操作抛出同一异常实例时，仍逐一清理并保留原异常。
     *
     * @throws NacosException 注册失败
     */
    @Test
    void repeatedCleanupExceptionDoesNotStopRemainingCleanup() throws NacosException {
        ConfigService client = mock(ConfigService.class);
        RuntimeException shared = new IllegalStateException("fixture cleanup failure");
        doThrow(shared).when(client).removeListener(anyString(), anyString(), any());
        CommonNacosConfigListener manager = new CommonNacosConfigListener(Map.of(
                "a", new RecordingConfig(client, "first", "group"),
                "b", new RecordingConfig(client, "second", "group")));
        manager.run();
        assertThatThrownBy(manager::close).isSameAs(shared);
        manager.close();
        verify(client).removeListener(eq("first"), eq("group"), any());
        verify(client).removeListener(eq("second"), eq("group"), any());
    }

    /**
     * 验证 Bean 名不作为 dataId，异步新值不被旧初始值覆盖，关闭不接收新值。
     *
     * @throws NacosException SDK 调用失败
     */
    @Test
    void usesActualKeysAndDoesNotOverwriteEarlyUpdates() throws NacosException {
        ConfigService client = mock(ConfigService.class);
        RecordingConfig config = new RecordingConfig(client, "actual-data", "group");
        AtomicReference<Listener> listener = new AtomicReference<>();
        when(client.getConfigAndSignListener(eq("actual-data"), eq("group"), eq(REQUEST_TIMEOUT), any()))
                .thenAnswer(invocation -> {
                    Listener value = invocation.getArgument(3);
                    listener.set(value);
                    value.receiveConfigInfo("newer");
                    return "older";
                });
        try (CommonNacosConfigListener manager = new CommonNacosConfigListener(
                Map.of("different-bean-name", config), REQUEST_TIMEOUT, 2)) {
            manager.run();
            manager.run();
            assertThat(config.values).containsExactly("newer");
            listener.get().receiveConfigInfo("next");
            listener.get().receiveConfigInfo(null);
            assertThat(config.values).containsExactly("newer", "next", null);
        }
        listener.get().receiveConfigInfo("after-close");
        assertThat(config.values).containsExactly("newer", "next", null);
        verify(client, times(1)).getConfigAndSignListener("actual-data", "group", REQUEST_TIMEOUT, listener.get());
        verify(client).removeListener("actual-data", "group", listener.get());
        verify(client, never()).shutDown();
    }

    /**
     * 验证空初始文本保留，后续注册失败清理之前及当前可能部分注册的监听器。
     *
     * @throws NacosException SDK 替身设置失败
     */
    @Test
    void startupFailureRollsBackAllRegistrations() throws NacosException {
        ConfigService firstClient = mock(ConfigService.class);
        ConfigService secondClient = mock(ConfigService.class);
        RecordingConfig first = new RecordingConfig(firstClient, "first", "group");
        RecordingConfig second = new RecordingConfig(secondClient, "second", "group");
        when(firstClient.getConfigAndSignListener(eq("first"), eq("group"), eq(REQUEST_TIMEOUT), any()))
                .thenReturn("");
        when(secondClient.getConfigAndSignListener(eq("second"), eq("group"), eq(REQUEST_TIMEOUT), any()))
                .thenThrow(new NacosException(500, "fixture failure"));
        try (CommonNacosConfigListener manager = new CommonNacosConfigListener(
                Map.of("a", first, "b", second), REQUEST_TIMEOUT, 2)) {
            assertThatThrownBy(manager::run).isInstanceOf(NacosException.class);
        }
        assertThat(first.values).containsExactly("");
        verify(firstClient).removeListener(eq("first"), eq("group"), any());
        verify(secondClient).removeListener(eq("second"), eq("group"), any());
        verify(firstClient, never()).shutDown();
        verify(secondClient, never()).shutDown();
    }

    /**
     * 验证网络初始化未结束时关闭，晚到的注册仍被移除且不回调业务。
     *
     * @throws Exception 测试等待或 SDK 调用失败
     */
    @Test
    void closesRegistrationThatFinishesAfterShutdown() throws Exception {
        ConfigService client = mock(ConfigService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        RecordingConfig config = new RecordingConfig(client, "data", "group");
        when(client.getConfigAndSignListener(eq("data"), eq("group"), eq(REQUEST_TIMEOUT), any()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    if (!proceed.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Fixture wait timed out");
                    }
                    return "late";
                });
        try (CommonNacosConfigListener manager = new CommonNacosConfigListener(
                Map.of("bean", config), REQUEST_TIMEOUT, 2);
             ThreadPoolExecutor callers = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                     new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy())) {
            var future = callers.submit(() -> {
                manager.run();
                return true;
            });
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                manager.close();
            } finally {
                proceed.countDown();
            }
            assertThat(future.get(3, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(config.values).isEmpty();
        verify(client).removeListener(eq("data"), eq("group"), any());
    }

    /**
     * 验证默认不连接，旧显式启用键兼容，新关闭键优先，以及可选依赖缺失安全。
     */
    @Test
    void requiresExplicitOptInAndHonorsOverrides() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(NacosConfiguration.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(NacosConfiguration.class));
        ConfigService custom = mock(ConfigService.class);
        runner.withBean(ConfigService.class, () -> custom)
                .withPropertyValues("spring.cloud.nacos.config.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(NacosConfiguration.class);
                    assertThat(context.getBean(ConfigService.class)).isSameAs(custom);
                });
        runner.withPropertyValues("spring.cloud.nacos.config.enabled=true", "common-tool.nacos.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NacosConfiguration.class));
        runner.withClassLoader(new FilteredClassLoader("com.alibaba.nacos"))
                .withPropertyValues("common-tool.nacos.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(NacosConfiguration.class));
    }

    /**
     * 验证新字段优先和属性透传，强制禁止记录全部 SDK 配置。
     */
    @Test
    void assemblesSdkPropertiesWithoutLoggingSecrets() {
        CommonNacosProperties properties = new CommonNacosProperties();
        properties.setServerAddr("127.0.0.1:18848");
        properties.setClientProperties(Map.of("customOption", "value", PropertyKeyConst.LOG_ALL_PROPERTIES, "true"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.cloud.nacos.config.server-addr", "127.0.0.1:8848")
                .withProperty("spring.cloud.nacos.config.namespace", "fixture");
        Properties sdk = new NacosConfiguration().assembleProperties(properties, environment);
        assertThat(sdk.getProperty(PropertyKeyConst.SERVER_ADDR)).isEqualTo("127.0.0.1:18848");
        assertThat(sdk.getProperty(PropertyKeyConst.NAMESPACE)).isEqualTo("fixture");
        assertThat(sdk.getProperty("customOption")).isEqualTo("value");
        assertThat(sdk.getProperty(PropertyKeyConst.LOG_ALL_PROPERTIES)).isEqualTo("false");
    }

    /**
     * 保存收到的配置值，用于验证顺序及删除通知。
     *
     * @author bytex0
     * @since 2026-10-06 18:32:38
     */
    private static final class RecordingConfig extends AbstractNacosConfig {

        /**
         * 配置所属客户端。
         */
        private final ConfigService client;

        /**
         * 实际配置标识。
         */
        private final String dataId;

        /**
         * 实际分组。
         */
        private final String group;

        /**
         * 可包含 null 删除通知的线程安全记录。
         */
        private final List<String> values = new CopyOnWriteArrayList<>();

        /**
         * 创建记录监听器。
         *
         * @param client 客户端
         * @param dataId 配置标识
         * @param group 配置分组
         */
        private RecordingConfig(ConfigService client, String dataId, String group) {
            this.client = client;
            this.dataId = dataId;
            this.group = group;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReceived(String content) {
            values.add(content);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDataId() {
            return dataId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getGroup() {
            return group;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ConfigService configService() {
            return client;
        }
    }
}
