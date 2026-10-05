package io.github.bytex0.lock.model;

import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.enums.RedisClientType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.concurrent.TimeUnit;

/**
 * 锁规则(LockRule)等待、租约及并发额度
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LockRule {

    /**
     * 是否启用，默认 true；null 等同关闭，不访问锁后端。
     */
    @Builder.Default
    private Boolean enable = true;

    /**
     * 作用域入口 true 最多等待 timeout，false 立即尝试，默认 true。
     * 原独立 lock 方法仍可中断地阻塞；原 tryLock 方法按 timeout 等待。
     */
    @Builder.Default
    private Boolean block = true;

    /**
     * 锁策略，恢复原模型默认 REDISSON_LOCK；注解默认仍是本地 REENTRANT_LOCK。
     */
    @Builder.Default
    private LockType lockType = LockType.REDISSON_LOCK;

    /**
     * 客户端选择，默认 REDISSON；信号量可选择 REDIS_TEMPLATE，其他 Redis 锁只支持 Redisson。
     */
    @Builder.Default
    private RedisClientType redisClientType = RedisClientType.REDISSON;

    /**
     * 非空业务键，应包含应用、租户及业务维度；相同策略与键共享状态。
     */
    private String key;

    /**
     * 信号量总额度，默认 1，必须大于零；同一活动键不允许配置不同总额度。
     */
    @Builder.Default
    private Integer permits = 1;

    /**
     * 本地锁和信号量是否采用公平排队，默认 true；不控制 Redis 后端的公平性。
     */
    @Builder.Default
    private Boolean fair = true;

    /**
     * 最长等待时间，默认 1000，单位由 timeUnit 指定，必须非负。
     */
    @Builder.Default
    private Long timeout = 1000L;

    /**
     * 显式锁租约，单位由 timeUnit 指定；0 使用 watchdog。
     * 信号量 0 使用 30 秒自动续租，显式值必须在 1 秒至 1 天之间。
     */
    @Builder.Default
    private long leaseTime = 0;

    /**
     * 等待和租约时间单位，默认毫秒，不能为 null；正数不足一毫秒时向上取整。
     */
    @Builder.Default
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    /**
     * 保留原九参数全字段构造器，新租约采用默认零值。
     *
     * @param enable 是否启用
     * @param block 是否阻塞
     * @param lockType 锁类型
     * @param redisClientType Redis 客户端类型
     * @param key 业务键
     * @param permits 总额度
     * @param fair 本地公平模式
     * @param timeout 等待时长
     * @param timeUnit 时间单位
     */
    public LockRule(Boolean enable, Boolean block, LockType lockType, RedisClientType redisClientType, String key,
                    Integer permits, Boolean fair, Long timeout, TimeUnit timeUnit) {
        this(enable, block, lockType, redisClientType, key, permits, fair, timeout, 0L, timeUnit);
    }

    /**
     * 保留当前版本的启用判断访问器。
     *
     * @return null 视为 false
     */
    public Boolean isEnable() {
        return Boolean.TRUE.equals(enable);
    }

    /**
     * 保留当前版本的阻塞判断访问器。
     *
     * @return null 视为 false
     */
    public Boolean isBlock() {
        return Boolean.TRUE.equals(block);
    }

    /**
     * 保留当前版本的公平判断访问器。
     *
     * @return null 视为 false
     */
    public Boolean isFair() {
        return Boolean.TRUE.equals(fair);
    }
}
