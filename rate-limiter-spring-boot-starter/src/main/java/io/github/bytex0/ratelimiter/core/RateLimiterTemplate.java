package io.github.bytex0.ratelimiter.core;

import com.google.common.util.concurrent.RateLimiter;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.model.FlowRule;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.redisson.api.RScript;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 限流执行(RateLimiterTemplate)原子额度、有界本地状态及服务端时钟
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
public class RateLimiterTemplate {

    /**
     * 按需获取Redis连接
     */
    private final Supplier<RedissonClient> redis;

    /**
     * 本地状态表，在其监视器内修改
     */
    private final Map<String, LocalState> local = new HashMap<>();

    /**
     * 本地键上限，不通过驱逐活动状态绕过限额
     */
    private final int maxLocalKeys;

    /**
     * 单调时钟，可注入测试时钟
     */
    private final LongSupplier ticker;

    /**
     * 类路径中的确定性Lua脚本
     */
    private final Map<RateLimiterType, String> scripts = Map.of(
            RateLimiterType.REDIS_LUA_FIXED_WINDOW, script("fixed_window"),
            RateLimiterType.REDIS_LUA_SLIDING_WINDOW, script("sliding_window"),
            RateLimiterType.REDIS_LUA_TOKEN_BUCKET, script("token_bucket"),
            RateLimiterType.REDIS_LUA_LEAKY_BUCKET, script("leaky_bucket"));

    public RateLimiterTemplate(Supplier<RedissonClient> redis, int maxLocalKeys) {
        this(redis, maxLocalKeys, System::nanoTime);
    }

    public RateLimiterTemplate(Supplier<RedissonClient> redis, int maxLocalKeys, LongSupplier ticker) {
        Assert.isTrue(maxLocalKeys > 0, "本地限流键上限必须大于0");
        this.redis = redis;
        this.maxLocalKeys = maxLocalKeys;
        this.ticker = ticker;
    }

    public boolean tryAccess(FlowRule rule) {
        Assert.notNull(rule, "限流规则不能为空");
        if (!rule.isEnable()) { return true; }
        Assert.hasText(rule.getKey(), "限流key不能为空");
        Assert.notNull(rule.getRateLimiterType(), "限流策略不能为空");
        Assert.isTrue(rule.getMaxRequests() > 0 && rule.getMaxRequests() <= 1000000
                && rule.getWindowTime() > 0 && rule.getWindowTime() <= 86400
                && rule.getBucketCapacity() > 0 && rule.getBucketCapacity() <= 1000000
                && rule.getTokenRate() > 0 && rule.getTokenRate() <= 1000000
                && rule.getPermits() > 0 && rule.getPermits() <= 10000, "限流参数不合法");
        RateLimiterType type = rule.getRateLimiterType();
        boolean bucket = type == RateLimiterType.REDIS_LUA_TOKEN_BUCKET || type == RateLimiterType.REDIS_LUA_LEAKY_BUCKET;
        Assert.isTrue(rule.getPermits() <= (bucket ? rule.getBucketCapacity() : rule.getMaxRequests()), "申请额度超过容量");
        String policy = switch (type) {
            case REDIS_LUA_TOKEN_BUCKET, REDIS_LUA_LEAKY_BUCKET -> rule.getBucketCapacity() + ":" + rule.getTokenRate();
            case GUAVA -> rule.getTokenRate() + ":" + rule.getWindowTime();
            default -> rule.getMaxRequests() + ":" + rule.getWindowTime();
        };
        String key = "common-tool:rate:" + type + ":" + MethodExpressionEvaluator.digest(rule.getKey() + "\0" + policy);
        if (type == RateLimiterType.LOCAL || type == RateLimiterType.GUAVA) {
            return localAccess(key, rule);
        }
        RedissonClient client = redis.get();
        Assert.state(client != null, "分布式限流需要RedissonClient");
        if (type == RateLimiterType.REDISSON) {
            var limiter = client.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, rule.getMaxRequests(), Duration.ofSeconds(rule.getWindowTime()),
                    Duration.ofSeconds(rule.getWindowTime() * 2L + 1));
            return limiter.tryAcquire(rule.getPermits());
        }
        Number result = client.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, scripts.get(type),
                RScript.ReturnType.LONG, List.of(key), rule.getWindowTime() * 1000L, rule.getMaxRequests(),
                rule.getBucketCapacity(), rule.getTokenRate(), rule.getPermits(), UUID.randomUUID().toString());
        return result.longValue() == 1;
    }

    private boolean localAccess(String key, FlowRule rule) {
        synchronized (local) {
            long now = ticker.getAsLong();
            long window = TimeUnit.SECONDS.toNanos(rule.getWindowTime());
            LocalState state = local.get(key);
            if (state == null) {
                if (local.size() >= maxLocalKeys) {
                    local.entrySet().removeIf(entry -> now - entry.getValue().lastAccess >= entry.getValue().idleTtl);
                }
                Assert.state(local.size() < maxLocalKeys, "本地限流状态已达上限");
                state = new LocalState(now, Math.max(window * 2, TimeUnit.MINUTES.toNanos(1)),
                        rule.getRateLimiterType() == RateLimiterType.GUAVA
                                ? RateLimiter.create(rule.getTokenRate(), rule.getWindowTime(), TimeUnit.SECONDS) : null);
                local.put(key, state);
            }
            state.lastAccess = now;
            if (state.guava != null) { return state.guava.tryAcquire(rule.getPermits()); }
            if (now - state.windowStart >= window) { state.windowStart = now; state.used = 0; }
            if (state.used + rule.getPermits() > rule.getMaxRequests()) { return false; }
            state.used += rule.getPermits();
            return true;
        }
    }

    private static String script(String name) {
        try {
            return new ClassPathResource("io/github/bytex0/ratelimiter/lua/" + name + ".lua")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    /**
     * 本地窗口(LocalState)受监视器保护的限流状态
     *
     * @author bytex0
     * @since 2026-10-05 16:50:07
     */
    private static class LocalState {

        /**
         * 窗口开始时钟
         */
        private long windowStart;

        /**
         * 最近访问时钟
         */
        private long lastAccess;

        /**
         * 可安全清理的空闲时长
         */
        private final long idleTtl;

        /**
         * 已用额度
         */
        private long used;

        /**
         * Guava策略实例，其他策略为空
         */
        private final RateLimiter guava;

        LocalState(long now, long idleTtl, RateLimiter guava) {
            windowStart = now; lastAccess = now; this.idleTtl = idleTtl; this.guava = guava;
        }
    }
}
