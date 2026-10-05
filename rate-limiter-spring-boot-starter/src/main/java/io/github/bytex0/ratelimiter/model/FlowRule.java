package io.github.bytex0.ratelimiter.model;

import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import lombok.Builder;
import lombok.Getter;

/**
 * 限流规则(FlowRule)明确保留Builder默认值
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@Getter
@Builder
public class FlowRule {

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enable = true;

    /**
     * 限流算法
     */
    @Builder.Default
    private RateLimiterType rateLimiterType = RateLimiterType.LOCAL;

    /**
     * 业务维度键
     */
    private String key;

    /**
     * 窗口内最大许可数
     */
    @Builder.Default
    private int maxRequests = 10;

    /**
     * 窗口秒数，GUAVA时为预热秒数
     */
    @Builder.Default
    private int windowTime = 1;

    /**
     * 桶容量
     */
    @Builder.Default
    private int bucketCapacity = 10;

    /**
     * 每秒补充或排出的额度
     */
    @Builder.Default
    private int tokenRate = 1;

    /**
     * 本次申请的许可数
     */
    @Builder.Default
    private int permits = 1;
}
