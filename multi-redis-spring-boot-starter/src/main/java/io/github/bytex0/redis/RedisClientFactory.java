package io.github.bytex0.redis;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Redis客户端(RedisClientFactory)创建边界，支持测试及应用定制
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
@FunctionalInterface
public interface RedisClientFactory {

    /**
     * 根据已预校验配置创建客户端，返回的实例所有权移交管理器。
     *
     * @param config SDK 连接配置
     * @return 非空客户端
     */
    RedissonClient create(Config config);
}
