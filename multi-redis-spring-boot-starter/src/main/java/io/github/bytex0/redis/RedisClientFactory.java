package io.github.bytex0.redis;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Redis客户端(RedisClientFactory)创建边界，支持测试及应用定制
 *
 * @author linshiqiang
 * @since 2026-10-05 16:18:09
 */
@FunctionalInterface
public interface RedisClientFactory {
    RedissonClient create(Config config);
}
