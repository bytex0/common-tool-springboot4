package io.github.bytex0.redis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.redisson.config.ReadMode;

import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多Redis(MultiRedisProperties)命名连接配置
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
@Getter
@Setter
@ConfigurationProperties("multi-redis")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MultiRedisProperties extends LegacyRedisOptions {

    /**
     * 配置序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 显式启用后才建立外部连接，默认 false；旧配置前缀也必须开启此开关。
     */
    private boolean enabled;

    /**
     * 默认客户端名称，默认 main，必须指向启用的连接。
     */
    private String primary = "main";

    /**
     * 命名连接，最多16个
     */
    private Map<String, Connection> clients = new LinkedHashMap<>();

    /**
     * 可选异步双写目标的客户端名称；默认无，不能与主客户端为同一实例。
     */
    private String backup;

    /**
     * 双写队列容量，默认 200，必须大于 0；满队列在主库操作前拒绝，不静默丢任务。
     */
    private int replicationQueueCapacity = 200;

    /**
     * 写操作排队、等待备库和关闭排空的上限，默认 10 秒，必须大于 0。
     */
    private Duration replicationTimeout = Duration.ofSeconds(10);

    /**
     * Redis连接(Connection)单机或集群参数
     *
     * @author bytex0
     * @since 2026-10-05 16:18:09
     */
    @Getter
    @Setter
    public static class Connection implements Serializable {

        /**
         * 连接配置序列化版本。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 是否建立该连接，默认 true；禁用连接不校验地址也不创建客户端。
         */
        private boolean enabled = true;

        /**
         * SINGLE 或 CLUSTER，不区分大小写，默认 SINGLE。
         */
        private String mode = "SINGLE";

        /**
         * 单机地址，无默认值，启用 SINGLE 时必填；密码不得嵌入 URL。
         */
        private String address;

        /**
         * 集群引导节点列表，默认空，启用 CLUSTER 时必须非空。
         */
        private List<String> nodes = List.of();

        /**
         * 单机逻辑库，默认 0，不得为负数；集群只能为 0。
         */
        private int database;

        /**
         * ACL 用户名，默认无，不输出到日志。
         */
        private String username;

        /**
         * 连接密码，默认无，通过外部配置传入，不输出到日志。
         */
        private String password;

        /**
         * STRING、JSON、KRYO、KRYO5 或 PROTOBUF，默认 STRING，不区分大小写。
         * JSON 不携带任意多态类型信息，二进制类型见 valueType 与 allowedTypes。
         */
        private String codec = "STRING";

        /**
         * 连接超时毫秒，默认 5000，必须大于 0。
         */
        private int connectTimeout = 5000;

        /**
         * 命令响应超时毫秒，默认 3000，必须大于 0。
         */
        private int timeout = 3000;

        /**
         * 单机或集群每个主节点的连接池上限，默认 8，必须大于 0。
         */
        private int poolSize = 8;

        /**
         * 每客户端 Netty 网络线程数，默认 2，必须大于 0。
         */
        private int nettyThreads = 2;

        /**
         * 可选机房别名；非空值不能与其他名称或别名冲突。
         */
        private String location;

        /**
         * 空闲回收毫秒，默认 10000，必须大于 0。
         */
        private int idleConnectionTimeout = 10000;

        /**
         * 发送重试次数，默认 3，不得小于 0。
         */
        private int retryAttempts = 3;

        /**
         * 固定发送重试间隔毫秒，默认 1000，必须大于 0。
         */
        private int retryInterval = 1000;

        /**
         * 从节点池上限，默认 8；仅用于 CLUSTER。
         */
        private int slavePoolSize = 8;

        /**
         * 读取模式，默认 MASTER；仅用于 CLUSTER。
         */
        private ReadMode readMode = ReadMode.MASTER;

        /**
         * 拓扑扫描间隔毫秒，默认 5000；仅用于 CLUSTER。
         */
        private int scanInterval = 5000;

        /**
         * 启动检查槽位覆盖，默认 true；仅用于 CLUSTER。
         */
        private boolean checkSlotsCoverage = true;

        /**
         * 是否检查锁的从库同步数量，默认 false，沿用旧模块行为。
         */
        private boolean checkLockSyncedSlaves;

        /**
         * 锁的从库同步等待毫秒，默认 1000，必须大于 0。
         */
        private long slavesSyncTimeout = 1000;

        /**
         * JSON/PROTOBUF 指定值类型，默认 Object；JSON 默认只反序列化为数据。
         * PROTOBUF 必须指定具体模型类，不接受 Object。
         */
        private Class<?> valueType = Object.class;

        /**
         * KRYO/KRYO5 的允许类型；默认只允许基础标量和字节数组，业务模型必须显式列出。
         */
        private List<Class<?>> allowedTypes = List.of();
    }
}
