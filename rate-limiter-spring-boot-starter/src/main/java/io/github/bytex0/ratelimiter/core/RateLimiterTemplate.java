package io.github.bytex0.ratelimiter.core;

import com.google.common.util.concurrent.RateLimiter;
import io.github.bytex0.ratelimiter.enums.RateLimiterType;
import io.github.bytex0.ratelimiter.enums.RedisClientType;
import io.github.bytex0.ratelimiter.model.FlowRule;
import io.github.bytex0.ratelimiter.manager.LuaScriptManager;
import io.github.bytex0.util.MethodExpressionEvaluator;
import org.redisson.api.RScript;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
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
     * 默认本地业务键上限，也用于独立构造 Guava 策略时的状态约束。
     */
    public static final int DEFAULT_MAX_LOCAL_KEYS = 10000;

    /**
     * 单个窗口或桶允许配置的最大额度。
     */
    private static final int MAX_CAPACITY = 1000000;

    /**
     * 单次申请许可数的安全上限。
     */
    private static final int MAX_PERMITS = 10000;

    /**
     * 窗口时长上限，单位为秒。
     */
    private static final int MAX_WINDOW_SECONDS = 86400;

    /**
     * 按需获取Redis连接
     */
    private final Supplier<RedissonClient> redis;

    /**
     * 按需获取 Spring Data Redis 模板，不使用其业务序列化器编码脚本参数。
     */
    private final Supplier<RedisTemplate<?, ?>> redisTemplate;

    /**
     * 本地状态表，所有读写均由 localLock 保护。
     */
    private final Map<String, LocalState> local = new HashMap<>();

    /**
     * 保护本地状态创建、窗口更新、容量检查及回收的复合操作。
     */
    private final ReentrantLock localLock = new ReentrantLock();

    /**
     * 本地键上限，不通过驱逐活动状态绕过限额
     */
    private final int maxLocalKeys;

    /**
     * 单调时钟，可注入测试时钟
     */
    private final LongSupplier ticker;

    /**
     * 创建仅提供 Redisson 后端的模板，保留现有构造入口。
     *
     * @param redis Redisson 客户端提供器，本地算法允许其返回 null
     * @param maxLocalKeys 本地业务键上限，必须大于零
     */
    public RateLimiterTemplate(Supplier<RedissonClient> redis, int maxLocalKeys) {
        this(redis, maxLocalKeys, System::nanoTime);
    }

    /**
     * 创建可指定单调时钟的模板，用于确定性验证本地窗口。
     *
     * @param redis Redisson 客户端提供器
     * @param maxLocalKeys 本地业务键上限
     * @param ticker 返回纳秒值的单调时钟
     */
    public RateLimiterTemplate(Supplier<RedissonClient> redis, int maxLocalKeys, LongSupplier ticker) {
        this(redis, () -> null, maxLocalKeys, ticker);
    }

    /**
     * 创建支持两种 Redis 后端的模板，不在构造时连接外部服务。
     *
     * @param redis Redisson 客户端提供器
     * @param redisTemplate RedisTemplate 提供器，仅选择该后端时才求值
     * @param maxLocalKeys 本地业务键上限
     * @param ticker 本地状态的单调纳秒时钟
     */
    public RateLimiterTemplate(Supplier<RedissonClient> redis, Supplier<RedisTemplate<?, ?>> redisTemplate,
                               int maxLocalKeys, LongSupplier ticker) {
        Assert.isTrue(maxLocalKeys > 0, "本地限流键上限必须大于0");
        Assert.notNull(redis, "Redisson提供器不能为空");
        Assert.notNull(redisTemplate, "RedisTemplate提供器不能为空");
        Assert.notNull(ticker, "单调时钟不能为空");
        this.redis = redis;
        this.redisTemplate = redisTemplate;
        this.maxLocalKeys = maxLocalKeys;
        this.ticker = ticker;
    }

    /**
     * 判断并原子消耗额度。业务规则执行期间不得被调用方修改。
     *
     * @param rule 非空规则，关闭规则直接放行
     * @return 是否获得本次许可
     * @throws IllegalArgumentException 规则不合法
     * @throws IllegalStateException 所选客户端缺失、脚本结果异常或本地状态达到上限
     */
    public boolean tryAccess(FlowRule rule) {
        return tryAccess(rule, null);
    }

    /**
     * 按完整规则执行可选的自定义 Lua 脚本，复用相同校验和键命名。
     *
     * @param rule 非空规则
     * @param overrideScript 四种 Lua 类型的替代脚本，为 null 时使用默认脚本
     * @return 是否取得许可
     */
    public boolean tryAccess(FlowRule rule, String overrideScript) {
        Assert.notNull(rule, "限流规则不能为空");
        if (!Boolean.TRUE.equals(rule.getEnable())) {
            return true;
        }
        Assert.hasText(rule.getKey(), "限流key不能为空");
        Assert.notNull(rule.getRateLimiterType(), "限流策略不能为空");
        RateLimiterType type = rule.getRateLimiterType();
        Assert.isTrue(overrideScript == null || (type != RateLimiterType.LOCAL
                && type != RateLimiterType.GUAVA && type != RateLimiterType.REDISSON),
                "非Lua限流类型不能覆盖脚本");
        boolean bucket = type == RateLimiterType.REDIS_LUA_TOKEN_BUCKET || type == RateLimiterType.REDIS_LUA_LEAKY_BUCKET;
        positive(rule.getPermits(), MAX_PERMITS, "permits");
        if (bucket) {
            positive(rule.getBucketCapacity(), MAX_CAPACITY, "bucketCapacity");
            positive(rule.getTokenRate(), MAX_CAPACITY, "tokenRate");
        } else if (type == RateLimiterType.GUAVA) {
            positive(rule.getTokenRate(), MAX_CAPACITY, "tokenRate");
            Assert.isTrue(rule.getWindowTime() != null && rule.getWindowTime() >= 0
                    && rule.getWindowTime() <= MAX_WINDOW_SECONDS, "Guava预热秒数超出允许范围");
        } else {
            positive(rule.getMaxRequests(), MAX_CAPACITY, "maxRequests");
            positive(rule.getWindowTime(), MAX_WINDOW_SECONDS, "windowTime");
        }
        if (type != RateLimiterType.GUAVA) {
            Assert.isTrue(rule.getPermits() <= (bucket ? rule.getBucketCapacity() : rule.getMaxRequests()), "申请额度超过容量");
        }
        String policy = switch (type) {
            case REDIS_LUA_TOKEN_BUCKET, REDIS_LUA_LEAKY_BUCKET -> rule.getBucketCapacity() + ":" + rule.getTokenRate();
            case GUAVA -> rule.getTokenRate() + ":" + rule.getWindowTime();
            default -> rule.getMaxRequests() + ":" + rule.getWindowTime();
        };
        String key = "common-tool:rate:" + type + ":" + MethodExpressionEvaluator.digest(rule.getKey() + "\0" + policy);
        if (type == RateLimiterType.LOCAL || type == RateLimiterType.GUAVA) {
            return localAccess(key, rule);
        }
        if (type == RateLimiterType.REDISSON) {
            RedissonClient client = redis.get();
            Assert.state(client != null, "原生Redisson限流需要RedissonClient");
            var limiter = client.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, rule.getMaxRequests(), Duration.ofSeconds(rule.getWindowTime()),
                    Duration.ofSeconds(rule.getWindowTime() * 2L + 1));
            return limiter.tryAcquire(rule.getPermits());
        }
        return executeScript(overrideScript == null ? LuaScriptManager.getScript(type) : overrideScript,
                rule.getRedisClientType(), key,
                scriptNumber(rule.getWindowTime()) * 1000L, scriptNumber(rule.getMaxRequests()),
                scriptNumber(rule.getBucketCapacity()), scriptNumber(rule.getTokenRate()),
                rule.getPermits(), UUID.randomUUID().toString());
    }

    /**
     * 执行返回整数许可标志的 Lua 脚本，两种后端使用完全相同的键和 UTF-8 参数。
     *
     * @param source 可信脚本内容
     * @param backend 所选 Redis 后端
     * @param key 唯一脚本键，由调用方负责命名空间隔离
     * @param values 脚本标量参数，不可包含 null
     * @return 脚本返回 1 时允许访问
     */
    public boolean executeScript(String source, RedisClientType backend, String key, Object... values) {
        Assert.hasText(source, "限流脚本不能为空");
        Assert.hasText(key, "脚本键不能为空");
        Assert.notNull(backend, "Redis客户端类型不能为空");
        Number result;
        if (backend == RedisClientType.REDISSON) {
            RedissonClient client = redis.get();
            Assert.state(client != null, "所选限流后端需要RedissonClient");
            result = client.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, source,
                    RScript.ReturnType.LONG, List.of(key), values);
        } else {
            RedisTemplate<?, ?> template = redisTemplate.get();
            Assert.state(template != null, "所选限流后端需要RedisTemplate");
            byte[][] arguments = new byte[values.length + 1][];
            arguments[0] = key.getBytes(StandardCharsets.UTF_8);
            for (int index = 0; index < values.length; index++) {
                Assert.notNull(values[index], "脚本参数不能为空");
                arguments[index + 1] = values[index].toString().getBytes(StandardCharsets.UTF_8);
            }
            result = template.execute((RedisCallback<Long>) connection -> connection.scriptingCommands()
                    .eval(source.getBytes(StandardCharsets.UTF_8), ReturnType.INTEGER, 1, arguments));
        }
        Assert.state(result != null, "限流脚本未返回即时结果，不支持管道或延迟执行模式");
        return result.longValue() == 1;
    }

    /**
     * 校验可空整数配置并提供确定的参数错误，而不是自动拆箱空指针。
     *
     * @param value 待校验值
     * @param maximum 允许的最大值
     * @param name 参数名称
     */
    private static void positive(Integer value, int maximum, String name) {
        Assert.isTrue(value != null && value > 0 && value <= maximum, name + "超出允许范围");
    }

    /**
     * 将未被当前算法使用的可空字段编码为脚本占位零值；有效字段已经完成校验。
     *
     * @param value 规则字段值
     * @return 原值或零
     */
    private static int scriptNumber(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 在显式锁内完成本地窗口或 Guava 状态的查找、回收和许可更新，不访问网络。
     *
     * @param key 已隔离算法和配置的内部键
     * @param rule 已通过校验的规则
     * @return 是否得到许可
     */
    private boolean localAccess(String key, FlowRule rule) {
        localLock.lock();
        try {
            long now = ticker.getAsLong();
            long window = TimeUnit.SECONDS.toNanos(rule.getWindowTime());
            LocalState state = local.get(key);
            if (state == null) {
                if (local.size() >= maxLocalKeys) {
                    local.entrySet().removeIf(entry -> now - entry.getValue().lastAccess >= entry.getValue().idleTtl);
                }
                Assert.state(local.size() < maxLocalKeys, "本地限流状态已达上限");
                long debtBound = rule.getRateLimiterType() == RateLimiterType.GUAVA
                        ? window * 3 + TimeUnit.SECONDS.toNanos(MAX_PERMITS) / rule.getTokenRate() : window * 2;
                state = new LocalState(now, Math.max(debtBound, TimeUnit.MINUTES.toNanos(1)),
                        rule.getRateLimiterType() == RateLimiterType.GUAVA
                                ? RateLimiter.create(rule.getTokenRate(), rule.getWindowTime(), TimeUnit.SECONDS) : null);
                local.put(key, state);
            }
            state.lastAccess = now;
            if (state.guava != null) {
                return state.guava.tryAcquire(rule.getPermits());
            }
            if (now - state.windowStart >= window) {
                state.windowStart = now;
                state.used = 0;
            }
            if (state.used + rule.getPermits() > rule.getMaxRequests()) {
                return false;
            }
            state.used += rule.getPermits();
            return true;
        } finally {
            localLock.unlock();
        }
    }

    /**
     * 本地窗口(LocalState)受 localLock 保护的限流状态
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

        /**
         * 创建尚未消耗额度的本地状态。
         *
         * @param now 当前单调时钟值，单位为纳秒
         * @param idleTtl 不会提前释放许可债务的回收间隔，单位为纳秒
         * @param guava Guava 限流器，固定窗口时为空
         */
        LocalState(long now, long idleTtl, RateLimiter guava) {
            windowStart = now;
            lastAccess = now;
            this.idleTtl = idleTtl;
            this.guava = guava;
        }
    }
}
