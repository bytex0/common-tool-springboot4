package io.github.bytex0.lock.model;

import io.github.bytex0.lock.enums.LockType;
import lombok.Builder;
import lombok.Getter;
import java.util.concurrent.TimeUnit;

/**
 * 锁规则(LockRule)等待、租约及并发额度
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@Getter
@Builder
public class LockRule {

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enable = true;

    /**
     * true最多等待timeout，false立即尝试
     */
    @Builder.Default
    private boolean block = true;

    /**
     * 锁策略
     */
    @Builder.Default
    private LockType lockType = LockType.REENTRANT_LOCK;

    /**
     * 业务键
     */
    private String key;

    /**
     * 信号量总额度
     */
    @Builder.Default
    private int permits = 1;

    /**
     * 本地公平策略
     */
    @Builder.Default
    private boolean fair = true;

    /**
     * 最长等待时间
     */
    @Builder.Default
    private long timeout = 1000;

    /**
     * 显式锁租约，0使用watchdog；信号量0使用30秒自动续租
     */
    @Builder.Default
    private long leaseTime = 0;

    /**
     * 时间单位
     */
    @Builder.Default
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
}
