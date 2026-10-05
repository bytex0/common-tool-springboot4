package io.github.bytex0.redis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
public class MultiRedisProperties {

    /**
     * 显式启用后才建立外部连接
     */
    private boolean enabled;

    /**
     * 默认客户端名称
     */
    private String primary = "main";

    /**
     * 命名连接，最多16个
     */
    private Map<String, Connection> clients = new LinkedHashMap<>();

    /**
     * Redis连接(Connection)单机或集群参数
     *
     * @author bytex0
     * @since 2026-10-05 16:18:09
     */
    @Getter
    @Setter
    public static class Connection {

        /**
         * 是否建立该连接
         */
        private boolean enabled = true;

        /**
         * SINGLE或CLUSTER
         */
        private String mode = "SINGLE";

        /**
         * 单机地址，密码不得嵌入URL
         */
        private String address;

        /**
         * 集群节点列表
         */
        private List<String> nodes = List.of();

        /**
         * 单机逻辑库，集群只能为0
         */
        private int database;

        /**
         * ACL用户名
         */
        private String username;

        /**
         * 连接密码，不输出到日志
         */
        private String password;

        /**
         * STRING或JSON，JSON不携带任意多态类型信息
         */
        private String codec = "STRING";

        /**
         * 连接超时毫秒
         */
        private int connectTimeout = 5000;

        /**
         * 命令超时毫秒
         */
        private int timeout = 3000;

        /**
         * 每个节点的连接池上限
         */
        private int poolSize = 8;

        /**
         * 网络线程数
         */
        private int nettyThreads = 2;
    }
}
