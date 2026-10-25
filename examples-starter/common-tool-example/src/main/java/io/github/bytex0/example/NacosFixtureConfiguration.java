package io.github.bytex0.example;

import com.alibaba.nacos.api.config.ConfigService;
import io.github.bytex0.nacos.AbstractNacosConfig;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式联调才启用的 Nacos 接收者，dataId 必须为脚本生成的随机测试标识。
 *
 * @author bytex0
 * @since 2026-10-06 18:41:54
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "common-tool.nacos", name = "enabled", havingValue = "true")
public class NacosFixtureConfiguration {

    /**
     * 创建实际 Starter 监听的配置对象，Bean 名故意与 dataId 不同。
     *
     * @param client Starter 提供的客户端
     * @param dataId 随机测试标识
     * @param group 随机测试分组
     * @return 测试接收者
     */
    @Bean
    public FixtureConfig differentNacosBeanName(ConfigService client,
            @Value("${common-tool.nacos.test-data-id}") String dataId,
            @Value("${common-tool.nacos.test-group}") String group) {
        return new FixtureConfig(client, dataId, group);
    }

    /**
     * 只存储本次测试配置快照的接收者。
     *
     * @author bytex0
     * @since 2026-10-06 18:41:54
     */
    public static final class FixtureConfig extends AbstractNacosConfig {

        /**
         * 由 Spring 负责关闭的客户端。
         */
        private final ConfigService client;

        /**
         * 本次测试的随机配置标识。
         */
        private final String dataId;

        /**
         * 本次测试分组。
         */
        private final String group;

        /**
         * 接收到的原子配置快照。
         */
        private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(null, 0));

        /**
         * 创建受测试资源命名限制的接收者。
         *
         * @param client 客户端
         * @param dataId 必须以 common-tool-test- 加 32 位十六进制随机值组成
         * @param group 非空分组
         */
        public FixtureConfig(ConfigService client, String dataId, String group) {
            if (dataId == null || !dataId.matches("common-tool-test-[0-9a-f]{32}") || group == null || group.isBlank()) {
                throw new IllegalArgumentException("A dedicated random Nacos fixture identity is required");
            }
            this.client = client;
            this.dataId = dataId;
            this.group = group;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReceived(String content) {
            snapshot.updateAndGet(previous -> new Snapshot(content, previous.revision + 1));
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

        /**
         * 获取一致的接收状态快照。
         *
         * @return 接收状态
         */
        public Snapshot snapshot() {
            return snapshot.get();
        }
    }

    /**
     * 接收状态，不向客户端暴露 SDK 对象。
     *
     * @param content 配置值，删除时为空
     * @param revision 接收次数
     * @author bytex0
     * @since 2026-10-06 18:41:54
     */
    public record Snapshot(
            /**
             * 最近配置文本，null 表示删除或尚未接收。
             */
            String content,

            /**
             * 接收次数。
             */
            long revision) {
    }
}
