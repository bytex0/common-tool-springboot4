package io.github.bytex0.redis.example;

import io.github.bytex0.redis.MultiRedisManager;
import io.github.bytex0.redis.RedissonUtil;
import org.redisson.api.RLock;
import org.redisson.api.geo.GeoEntry;
import org.redisson.api.geo.GeoPosition;
import org.redisson.api.geo.GeoUnit;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 场景(RedisScenarioService)通过真实 Starter 工具执行分组操作并清理独立测试键。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:07:28
 */
@Service
public class RedisScenarioService {

    /**
     * 本示例复制确认上限。
     */
    private static final Duration WAIT = Duration.ofSeconds(10);

    /**
     * 普通数据的测试有效期。
     */
    private static final Duration TTL = Duration.ofSeconds(30);

    /**
     * 自动配置的完整工具，主路由场景验证真实双写。
     */
    private final RedissonUtil primary;

    /**
     * 自动配置的命名连接管理器。
     */
    private final MultiRedisManager manager;

    /**
     * Boot 的 Jackson 3 转换器。
     */
    private final ObjectMapper mapper;

    /**
     * 构造示例场景，不自行创建 SDK 客户端。
     *
     * @param primary 主库工具
     * @param manager 命名管理器
     * @param mapper JSON 转换器
     */
    public RedisScenarioService(RedissonUtil primary, MultiRedisManager manager, ObjectMapper mapper) {
        this.primary = primary;
        this.manager = manager;
        this.mapper = mapper;
    }

    /**
     * 执行一个分组场景；只接受 UUID 运行标识，不允许请求传入任意业务键。
     *
     * @param group 已知操作分组
     * @param client 客户端名称
     * @param run UUID 运行标识
     * @return 仅包含 JSON 数据的操作结果
     * @throws Exception 模型转换或锁等待失败时抛出
     */
    public Map<String, Object> run(String group, String client, String run) throws Exception {
        UUID identity = UUID.fromString(run);
        String key = "common-tool-test:{" + identity + "}:" + client;
        if ("typed".equals(group)) {
            return typed(client, key);
        }
        RedissonUtil util = "main".equals(client) ? primary : new RedissonUtil(manager.get(client), mapper);
        try (ScenarioScope scope = new ScenarioScope(util, key, util != primary)) {
            Map<String, Object> result = switch (group) {
                case "strings" -> strings(util, key);
                case "hash" -> hash(util, key);
                case "collections" -> collections(util, key);
                case "sorted" -> sorted(util, key);
                case "queues" -> queues(util, key);
                case "geo" -> geo(util, key);
                case "probabilistic" -> probabilistic(util, key);
                case "counters" -> counters(util, key);
                case "locks" -> locks(util, key);
                default -> throw new IllegalArgumentException("未知Redis操作分组");
            };
            util.awaitReplication(WAIT);
            result.put("replicationFailures", util.replicationFailures());
            if (util.getBackRedissonClient() != null) {
                try (RedissonUtil replica = new RedissonUtil(util.getBackRedissonClient(), mapper)) {
                    result.put("mirroredExists", replica.exists(key) == util.exists(key));
                    if ("strings".equals(group)) {
                        result.put("mirroredValue", replica.<String>get(key));
                    } else if ("queues".equals(group)) {
                        result.put("mirroredQueue", replica.<String>lrange(key));
                    } else if ("hash".equals(group)) {
                        result.put("mirroredHash", replica.<String, String>hgetAll(key));
                    }
                }
            }
            return result;
        }
    }

    /**
     * 用显式编码器测试 Bucket 和 Hash 模型，既不依赖 JSON 字符串替代，也不泄露 SDK 对象。
     *
     * @param client 命名客户端
     * @param key UUID 隔离的键
     * @return 还原后的字段值
     */
    private Map<String, Object> typed(String client, String key) {
        try (RedissonUtil util = new RedissonUtil(manager.get(client), mapper)) {
            try {
                RedisWireValue input = new RedisWireValue();
                input.setName("中文-typed");
                util.set(key, input);
                util.hset(key + ":b", "entry", input);
                RedisWireValue bucket = util.get(key);
                RedisWireValue field = util.hget(key + ":b", "entry");
                return Map.of("bucket", bucket.getName(), "hash", field.getName());
            } finally {
                util.delete(List.of(key, key + ":b"));
            }
        }
    }

    /**
     * 验证全部字符串写入重载、JSON 模型、NX 和 TTL。
     *
     * @param util 实际工具
     * @param key 隔离测试键
     * @return 操作结果
     * @throws Exception JSON 解析失败时抛出
     */
    private Map<String, Object> strings(RedissonUtil util, String key) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("set", util.set(key, "first"));
        result.put("plain", util.get(key));
        util.set(key, "codec", StringCodec.INSTANCE);
        result.put("codec", util.get(key, StringCodec.INSTANCE));
        util.set(key, "ttl", TTL);
        result.put("ttl", util.getExpire(key) > 0);
        util.set(key, "codec-ttl", TTL, StringCodec.INSTANCE);
        result.put("codecTtl", util.get(key, StringCodec.INSTANCE));
        result.put("nxFirst", util.setNx(key + ":nx", "owner", TTL));
        result.put("nxSecond", util.setNx(key + ":nx", "other", TTL));
        util.setSerialize(key, Map.of("name", "中文", "count", 3));
        result.put("json", util.get(key, Map.class));
        result.put("missing", util.get(key + ":missing", Map.class));
        util.set(key, "final");
        result.put("final", util.get(key));
        return result;
    }

    /**
     * 验证 Hash 操作、三个增量重载和模型转换。
     *
     * @param util 实际工具
     * @param key 隔离键
     * @return 操作结果
     */
    private Map<String, Object> hash(RedissonUtil util, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        util.hset(key, "name", "中文");
        util.hmset(key, Map.of("first", "1", "second", "2"));
        result.put("count", util.hgetCount(key));
        result.put("field", util.hget(key, "name"));
        result.put("model", util.hget(key, Map.class));
        result.put("longIncrement", util.<Long>hincrby(key, "number", 2L));
        result.put("doubleIncrement", util.<Double>hincrby(key, "number", 0.5));
        result.put("numberIncrement", util.<Double>hincrby(key, "number", (Number) 1.5));
        result.put("removed", util.hdel(key, "first", "second", "absent"));
        result.put("all", util.hgetAll(key));
        return result;
    }

    /**
     * 验证 Set 全部集合重载、随机读取与弹出，以及 List 顺序和区间。
     *
     * @param util 实际工具
     * @param key 隔离键
     * @return 操作结果
     */
    private Map<String, Object> collections(RedissonUtil util, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        util.sadd(key, "a", "b");
        util.saddAll(key, List.of("c"));
        util.saddAll(key, Set.of("d", "e"));
        result.put("members", util.smembers(key));
        result.put("contains", util.sismember(key, "a"));
        result.put("random", util.sRandom(key, ""));
        util.srem(key, "b");
        util.sremAll(key, List.of("c"));
        util.sremAll(key, Set.of("d", "e"));
        result.put("popped", util.spop(key));
        result.put("emptyPop", util.spop(key));
        util.sadd(key, "x", "y");
        result.put("batchPop", util.spop(key, 2));
        util.lpush(key + ":b", "a", "b", "a");
        util.lpushAll(key + ":b", List.of("c"));
        result.put("list", util.lrange(key + ":b"));
        result.put("range", util.lrange(key + ":b", 1, -1));
        result.put("listContains", util.lcontains(key + ":b", "b"));
        util.lrem(key + ":b", "a");
        util.lremAll(key + ":b", List.of("c"));
        result.put("remaining", util.lrange(key + ":b"));
        return result;
    }

    /**
     * 验证有序集合的分数、升降序、闭区间和批量操作。
     *
     * @param util 实际工具
     * @param key 隔离键
     * @return 操作结果
     */
    private Map<String, Object> sorted(RedissonUtil util, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", util.zadd(key, "a", 1));
        result.put("batchAdded", util.zaddAll(key, Map.of("b", 2.0, "c", 3.0)));
        result.put("ascending", util.zrangeByScore(key, 1, 2));
        result.put("score", util.zscore(key, "b"));
        result.put("rank", util.zrevrank(key, "c"));
        result.put("descending", util.zrevrange(key, 0, -1));
        Map<String, Double> scores = new LinkedHashMap<>();
        util.<String>zgetAllWithScores(key).forEach(entry -> scores.put(entry.getValue(), entry.getScore()));
        result.put("scores", scores);
        result.put("count", util.zcount(key, 1, 2));
        result.put("size", util.zcard(key));
        result.put("increment", util.zincrby(key, "a", 4));
        result.put("removed", util.zrem(key, "b", "c"));
        result.put("remaining", util.zrevrange(key, 0, -1));
        return result;
    }

    /**
     * 验证双端队列重复值方向和阻塞队列成功、超时路径。
     *
     * @param util 实际工具
     * @param key 隔离键
     * @return 操作结果
     */
    private Map<String, Object> queues(RedissonUtil util, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        util.offerFirst(key, "a");
        util.offerLast(key, "b");
        util.offerLast(key, "a");
        result.put("last", util.pollLast(key));
        result.put("contains", util.containDeque(key, "b"));
        util.dequeAddAll(key, List.of("c", "d"));
        result.put("first", util.pollFirst(key));
        util.dequeRemoveAll(key, List.of("d"));
        result.put("remaining", util.lrange(key));
        result.put("offered", util.offerBlockingQueue(key + ":b", "job"));
        result.put("taken", util.takeBlockingQueue(key + ":b"));
        util.offerBlockingQueue(key + ":b", "timed");
        result.put("polled", util.pollBlockingQueue(key + ":b", 1, TimeUnit.SECONDS));
        result.put("timeout", util.pollBlockingQueue(key + ":b", 20, TimeUnit.MILLISECONDS));
        return result;
    }

    /**
     * 验证 GEO 的单条、批量、距离、搜索结果和删除。
     *
     * @param util 实际工具
     * @param key 隔离键
     * @return 可序列化结果，不暴露 SDK 类型
     */
    private Map<String, Object> geo(RedissonUtil util, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", util.addGeoLocation(key, 13.361389, 38.115556, "Palermo"));
        result.put("batchAdded", util.addGeoLocation(key, new GeoEntry(15.087269, 37.502669, "Catania")));
        GeoPosition position = util.getGeoPosition(key, "Palermo").get("Palermo");
        result.put("position", List.of(position.getLongitude(), position.getLatitude()));
        result.put("distance", util.getDistance(key, "Palermo", "Catania", GeoUnit.KILOMETERS));
        result.put("search", util.searchGeo(key, 15, 37, 200, GeoUnit.KILOMETERS));
        result.put("distances", util.searchGeoWithDistance(key, 15, 37, 200, GeoUnit.KILOMETERS));
        result.put("positions", util.searchGeoWithPosition(key, 15, 37, 200, GeoUnit.KILOMETERS).keySet());
        result.put("removed", util.removeGeoLocation(key, "Palermo"));
        result.put("batchRemoved", util.removeGeoLocations(key, List.of("Catania")));
        return result;
    }

    /**
     * 验证布隆过滤器、位图三个运算以及 HyperLogLog 并集。
     *
     * @param util 实际工具
     * @param key 隔离键，同一组键共享集群 hash tag
     * @return 操作结果
     */
    private Map<String, Object> probabilistic(RedissonUtil util, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        String bloom = key + ":bloom";
        result.put("bloomCreated", util.createBloomFilter(bloom, 100, 0.01));
        result.put("bloomRepeated", util.createBloomFilter(bloom, 100, 0.01));
        result.put("bloomAdded", util.addToBloomFilter(bloom, "member"));
        result.put("bloomContains", util.mightContainInBloomFilter(bloom, "member"));
        result.put("bloomCount", util.getBloomFilterCount(bloom));
        result.put("bloomSize", util.getBloomFilterSize(bloom));
        result.put("bloomRate", util.getBloomFilterFalseProbability(bloom));
        result.put("bloomDeleted", util.deleteBloomFilter(bloom));
        result.put("oldBit", util.setBit(key, 1, true));
        util.setBit(key, 2, true);
        util.setBit(key + ":b", 2, true);
        result.put("bit", util.getBit(key, 1));
        result.put("bitCount", util.bitCount(key));
        util.bitAnd(key + ":c", key, key + ":b");
        result.put("and", util.bitCount(key + ":c"));
        util.bitOr(key + ":c", key, key + ":b");
        result.put("or", util.bitCount(key + ":c"));
        util.bitXor(key + ":c", key, key + ":b");
        result.put("xor", util.bitCount(key + ":c"));
        util.clearBitSet(key);
        result.put("cleared", util.bitCount(key));
        util.delete(List.of(key, key + ":b", key + ":c"));
        result.put("pfadd", util.pfadd(key, "a"));
        result.put("pfbatch", util.pfaddAll(key, List.of("a", "b")));
        util.pfaddAll(key + ":b", List.of("b", "c"));
        result.put("pfcount", util.pfcount(key));
        result.put("pfunion", util.pfcountUnion(List.of(key, key + ":b")));
        result.put("pfempty", util.pfcountUnion(List.of()));
        result.put("pfmerge", util.pfmerge(key + ":c", List.of(key, key + ":b")));
        result.put("pfdeleted", util.pfdelete(key + ":c"));
        return result;
    }

    /**
     * 验证整数精度、滑动过期以及通用键操作的真实布尔结果。
     *
     * @param util 实际工具
     * @param key 隔离键
     * @return 操作结果
     */
    private Map<String, Object> counters(RedissonUtil util, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("first", util.increment(key, TTL));
        result.put("second", util.increment(key, 4, TTL));
        result.put("decrement", util.decrement(key));
        result.put("double", util.incrementDouble(key + ":b", 1.25, TTL));
        result.put("large", util.increment(key + ":c", 9007199254740993L, TTL));
        result.put("exists", util.exists(key));
        result.put("expired", util.expire(key, TTL));
        result.put("ttl", util.getExpire(key) > 0);
        result.put("deleted", util.delete(key));
        result.put("missingDelete", util.delete(key));
        result.put("missingExpire", util.expire(key, TTL));
        result.put("missingTtl", util.getExpire(key));
        util.delete(List.of(key + ":b", key + ":c"));
        result.put("batchDeleted", !util.exists(key + ":b") && !util.exists(key + ":c"));
        return result;
    }

    /**
     * 验证普通、公平和读写锁，并在原线程释放取得的全部锁。
     *
     * @param util 实际工具
     * @param key 隔离锁名称
     * @return 获取结果
     * @throws InterruptedException 等待锁被中断时抛出
     */
    private Map<String, Object> locks(RedissonUtil util, String key) throws InterruptedException {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean acquired = util.tryLock(key, 1, 5, TimeUnit.SECONDS);
        try {
            result.put("normal", acquired);
        } finally {
            if (acquired) {
                util.getLock(key).unlock();
            }
        }
        for (Map.Entry<String, RLock> entry : Map.of("fair", util.getFairLock(key + ":b"),
                "read", util.getReadWriteLock(key + ":c").readLock(),
                "write", util.getReadWriteLock(key + ":nx").writeLock()).entrySet()) {
            RLock lock = entry.getValue();
            boolean locked = lock.tryLock(1, 5, TimeUnit.SECONDS);
            try {
                result.put(entry.getKey(), locked);
            } finally {
                if (locked) {
                    lock.unlock();
                }
            }
        }
        return result;
    }

    /**
     * 场景资源(ScenarioScope)确保失败时也清理本次运行独有的键。
     *
     * @param util 使用的工具
     * @param key 本次运行的键前缀
     * @param owned 是否需要关闭临时工具
     * @author linshiqiang
     * @since 2026-10-06 10:07:28
     */
    private record ScenarioScope(
            /**
             * 场景实际使用的工具，不拥有客户端。
             */
            RedissonUtil util,

            /**
             * UUID 隔离的键前缀。
             */
            String key,

            /**
             * 是否拥有临时工具的关闭责任。
             */
            boolean owned) implements AutoCloseable {

        /**
         * 清理布隆配置与普通键，再等待备库清理完成。
         */
        @Override
        public void close() {
            try {
                util.deleteBloomFilter(key + ":bloom");
            } finally {
                try {
                    util.delete(List.of(key, key + ":b", key + ":c", key + ":nx"));
                    util.awaitReplication(WAIT);
                    Assert.state(!util.exists(key) && !util.exists(key + ":b"), "测试资源清理失败");
                } finally {
                    if (owned) {
                        util.close();
                    }
                }
            }
        }
    }
}
