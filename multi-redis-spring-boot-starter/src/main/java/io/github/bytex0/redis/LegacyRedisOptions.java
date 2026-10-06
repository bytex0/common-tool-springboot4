package io.github.bytex0.redis;

import lombok.Getter;
import lombok.Setter;
import org.redisson.config.ReadMode;

import java.io.Serializable;
import java.util.List;

/**
 * 旧版 Redis 配置(LegacyRedisOptions)保留原属性模型，供两种配置前缀复用。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:58:09
 */
@Getter
@Setter
public class LegacyRedisOptions implements Serializable {

    /**
     * 本版本配置模型的序列化标识；不承诺与旧包名的 Java 序列化兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 公共密码，默认无；集群级非空密码优先，禁止输出到日志。
     */
    private String password;

    /**
     * 建连超时毫秒，默认 5000，必须大于 0。
     */
    private Integer connectionTimeout = 5000;

    /**
     * 命令响应超时毫秒，默认 3000，必须大于 0。
     */
    private int responseTimeout = 3000;

    /**
     * 超出最小池大小的空闲连接回收时间，毫秒，默认 10000。
     */
    private int idleConnectionTimeout = 10000;

    /**
     * 是否检查锁在从节点上的同步数量，沿用旧默认 false。
     */
    private boolean checkLockSyncedSlaves;

    /**
     * 锁相关从节点同步等待毫秒，默认 1000，必须大于 0。
     */
    private long slavesSyncTimeout = 1000;

    /**
     * 默认集群；显式开启 Starter 后配置 nodes 即启用，保持旧 active 对主库无效的行为。
     */
    private Cluster cluster = new Cluster();

    /**
     * 第二集群，active=true 时作为异步双写目标。
     */
    private Cluster cluster2 = new Cluster();

    /**
     * 第三集群，active=true 时建立客户端和位置路由，不默认参与双写。
     */
    private Cluster cluster3 = new Cluster();

    /**
     * 每个主节点最大连接数，默认 100，必须大于 0。
     */
    private Integer masterConnectionPoolSize = 100;

    /**
     * 每个从节点最大连接数，默认 128，必须大于 0。
     */
    private int slaveConnectionPoolSize = 128;

    /**
     * 发送失败重试次数，默认 3，不得小于 0。
     */
    private int retryAttempts = 3;

    /**
     * 发送重试间隔毫秒，默认 1000，必须大于 0。
     */
    private int retryInterval = 1000;

    /**
     * 集群读取节点策略，沿用旧默认 MASTER。
     */
    private ReadMode readMode = ReadMode.MASTER;

    /**
     * 集群拓扑扫描间隔毫秒，默认 5000，必须大于 0。
     */
    private int scanInterval = 5000;

    /**
     * 启动时检查槽位覆盖，默认 true。
     */
    private boolean checkSlotsCoverage = true;

    /**
     * 默认 STRING；支持 JSON、KRYO、KRYO5、PROTOBUF，未知值明确失败。
     */
    private String codecType = "STRING";

    /**
     * 旧 Jedis 兼容属性；原 Redisson 实现未使用，新版同样不创建 Jedis 连接。
     */
    private Jedis jedis;

    /**
     * 集群配置(Cluster)保留节点、机房位置和覆盖密码。
     *
     * @author linshiqiang
     * @since 2026-10-06 09:58:09
     */
    @Getter
    @Setter
    public static class Cluster implements Serializable {

        /**
         * 配置序列化版本。
         */
        private static final long serialVersionUID = 1L;

        /**
         * Netty 线程数，默认 32，必须大于 0。
         */
        private int nettyThreads = 32;

        /**
         * 集群密码；非空时覆盖公共密码。
         */
        private String password;

        /**
         * 启动节点列表；启用集群时不能为空，地址不得包含凭据。
         */
        private List<String> nodes = List.of();

        /**
         * 旧未生效的重定向参数，保留访问器；Redisson 自行处理 MOVED/ASK。
         */
        private Integer maxRedirects = 3;

        /**
         * 第二、第三集群的启用开关，默认 false。
         */
        private Boolean active = false;

        /**
         * 可选机房别名，非空值必须唯一；缺省仍可按命名客户端访问。
         */
        private String location;
    }

    /**
     * Jedis 兼容属性(Jedis)仅用于保留原模型，不参与 Redisson 配置。
     *
     * @author linshiqiang
     * @since 2026-10-06 09:58:09
     */
    @Getter
    @Setter
    public static class Jedis implements Serializable {

        /**
         * 配置序列化版本。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 旧连接池属性，默认无；原模块没有使用该配置。
         */
        private Pool pool;
    }

    /**
     * Jedis 池模型(Pool)保留原字段和默认值，不伪装为生效的 Redisson 参数。
     *
     * @author linshiqiang
     * @since 2026-10-06 09:58:09
     */
    @Getter
    @Setter
    public static class Pool implements Serializable {

        /**
         * 配置序列化版本。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 旧最大空闲数，默认 50，未参与原 Redisson 实现。
         */
        private int maxIdle = 50;

        /**
         * 旧最小空闲数，默认 10，未参与原 Redisson 实现。
         */
        private int minIdle = 10;

        /**
         * 旧最大连接数，默认 200，未参与原 Redisson 实现。
         */
        private int maxActive = 200;

        /**
         * 旧借用等待毫秒，默认 -1，未参与原 Redisson 实现。
         */
        private long maxWait = -1;

        /**
         * 旧逐出检查间隔毫秒，默认 -1，未参与原 Redisson 实现。
         */
        private long timeBetweenEvictionRuns = -1;
    }
}
