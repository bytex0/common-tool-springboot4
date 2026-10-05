package io.github.bytex0.ratelimiter.model;

import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.enums.RedisClientType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 限流规则(FlowRule)明确保留Builder默认值
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowRule {

    /**
     * 是否启用，默认 true；false 或 null 表示不执行限流。
     */
    @Builder.Default
    private Boolean enable = true;

    /**
     * 限流算法，保留原模型默认滑动窗口；启用时不能为空。
     */
    @Builder.Default
    private RateLimiterType rateLimiterType = RateLimiterType.REDIS_LUA_SLIDING_WINDOW;

    /**
     * Lua 算法执行后端，默认 Redisson；不改变业务键和额度，原生 REDISSON 算法忽略该项。
     */
    @Builder.Default
    private RedisClientType redisClientType = RedisClientType.REDISSON;

    /**
     * 业务维度键，启用时不能为空；模板会使用摘要编码，不能直接传入未经授权的 SpEL。
     */
    private String key;

    /**
     * 窗口内最大许可数，默认 50，取值 1 至 1000000。
     */
    @Builder.Default
    private Integer maxRequests = 50;

    /**
     * 窗口秒数，默认 1，取值 1 至 86400；GUAVA 时为预热秒数，允许为 0。
     */
    @Builder.Default
    private Integer windowTime = 1;

    /**
     * 桶容量，默认 50，取值 1 至 1000000，适用于令牌桶和漏桶。
     */
    @Builder.Default
    private Integer bucketCapacity = 50;

    /**
     * 每秒补充或排出的额度，默认 10，取值 1 至 1000000，GUAVA 中表示每秒速率。
     */
    @Builder.Default
    private Integer tokenRate = 10;

    /**
     * 本次申请的许可数，默认 1，取值 1 至 10000；窗口及桶策略不能超过对应容量。
     */
    @Builder.Default
    private Integer permits = 1;

    /**
     * 保留便捷判断入口，同时由 Lombok 提供原模型的 getEnable/setEnable。
     * 使用包装返回类型，避免 JavaBeans 将其选为与 Boolean setter 不匹配的 primitive getter。
     *
     * @return 非空布尔值，仅 enable 为 true 时返回 true
     */
    public Boolean isEnable() {
        return Boolean.TRUE.equals(enable);
    }
}
