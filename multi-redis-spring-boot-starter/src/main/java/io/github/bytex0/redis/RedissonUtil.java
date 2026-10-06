package io.github.bytex0.redis;

import org.redisson.api.geo.GeoEntry;
import org.redisson.api.geo.GeoOrder;
import org.redisson.api.geo.GeoPosition;
import org.redisson.api.geo.GeoUnit;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.util.Assert;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Redis 工具(RedissonUtil)提供原版数据结构操作、显式位置路由与有界异步双写。
 * 读取、NX 和锁只访问主库；其他写操作成功后可复制到备库，不构成跨库事务。
 * 输入对象及集合元素在复制确认前应保持不可变；批量容器会在调用时复制。
 * 客户端及外部执行器由调用方或 Spring 管理，本工具关闭只停止写入并等待复制。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:00:40
 */
public class RedissonUtil implements AutoCloseable {

    /**
     * 默认主写排队和停止等待时间。
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 默认待复制任务上限。
     */
    private static final int DEFAULT_CAPACITY = 200;

    /**
     * 将整数递增和滑动有效期放在同一个 Redis 原子脚本中，字符串返回保留长整数精度。
     */
    private static final String INCREMENT = "redis.call('INCRBY', KEYS[1], ARGV[1]); "
            + "redis.call('PEXPIRE', KEYS[1], ARGV[2]); return redis.call('GET', KEYS[1])";

    /**
     * 将浮点递增和有效期更新合并为原子操作。
     */
    private static final String INCREMENT_DOUBLE = "redis.call('INCRBYFLOAT', KEYS[1], ARGV[1]); "
            + "redis.call('PEXPIRE', KEYS[1], ARGV[2]); return redis.call('GET', KEYS[1])";

    /**
     * 实际主客户端，不拥有关闭权。
     */
    private final RedissonClient primary;

    /**
     * 可选备客户端，不拥有关闭权。
     */
    private final RedissonClient backup;

    /**
     * 受上下文隔离的路由函数。
     */
    private final Function<String, RedissonClient> router;

    /**
     * Jackson 3 显式模型转换器，不启用默认多态类型。
     */
    private final ObjectMapper mapper;

    /**
     * 主写顺序、复制容量及失败状态。
     */
    private final RedisWriteCoordinator writes;

    /**
     * 创建仅主库工具，客户端仍由调用方关闭。
     *
     * @param primary 主客户端
     * @param mapper Jackson 3 转换器
     */
    public RedissonUtil(RedissonClient primary, ObjectMapper mapper) {
        this(primary, null, mapper, null, DEFAULT_CAPACITY, DEFAULT_TIMEOUT);
    }

    /**
     * 创建显式主备工具，执行器和客户端均由调用方管理。
     *
     * @param primary 主客户端
     * @param backup 可选备客户端
     * @param mapper JSON 转换器
     * @param executor 备写执行器
     * @param capacity 待复制任务容量
     * @param timeout 排队与停止等待上限
     */
    public RedissonUtil(RedissonClient primary, RedissonClient backup, ObjectMapper mapper,
                        ExecutorService executor, int capacity, Duration timeout) {
        this(primary, backup, mapper, executor, capacity, timeout, name -> {
            throw new IllegalArgumentException("未配置Redis位置路由");
        });
    }

    /**
     * 使用管理器的命名和位置路由创建工具。
     *
     * @param manager 客户端管理器
     * @param properties 复制配置
     * @param mapper JSON 转换器
     * @param executor 有界任务执行器
     */
    public RedissonUtil(MultiRedisManager manager, MultiRedisProperties properties, ObjectMapper mapper,
                        ExecutorService executor) {
        this(manager.primary(), properties.getBackup() == null ? null : manager.get(properties.getBackup()),
                mapper, executor, properties.getReplicationQueueCapacity(), properties.getReplicationTimeout(),
                manager::get);
    }

    /**
     * 装配工具的固定依赖。
     *
     * @param primary 主客户端
     * @param backup 可选备客户端
     * @param mapper JSON 转换器
     * @param executor 复制执行器
     * @param capacity 复制容量
     * @param timeout 等待上限
     * @param router 命名路由
     */
    private RedissonUtil(RedissonClient primary, RedissonClient backup, ObjectMapper mapper,
                         ExecutorService executor, int capacity, Duration timeout,
                         Function<String, RedissonClient> router) {
        Assert.notNull(mapper, "JSON转换器不能为空");
        this.primary = primary;
        this.backup = backup;
        this.mapper = mapper;
        this.router = router;
        this.writes = new RedisWriteCoordinator(primary, backup, executor, capacity, timeout);
    }

    /**
     * 保留旧初始化入口；路由现已在构造时完成，不写入静态全局表。
     */
    public void initClient() {
        Assert.notNull(primary, "主Redis客户端不能为空");
    }

    /**
     * 获取主客户端，调用方不能关闭容器管理的实例。
     *
     * @return 主客户端
     */
    public RedissonClient getRedissonClient() {
        return primary;
    }

    /**
     * 获取可选备客户端。
     *
     * @return 备客户端，未配置时为 null
     */
    public RedissonClient getBackRedissonClient() {
        return backup;
    }

    /**
     * 按命名或机房位置获取客户端，未知位置明确报错。
     *
     * @param location 配置的位置或名称
     * @return 对应共享客户端
     */
    public RedissonClient getRedissonClient(String location) {
        return router.apply(location);
    }

    /**
     * 设置无过期值，覆盖旧值和旧 TTL。
     *
     * @param key 键
     * @param value 值
     * @param <V> 值类型
     * @return 成功时 true，错误直接抛出
     */
    public <V> Boolean set(String key, V value) {
        key(key);
        return write(client -> {
            client.getBucket(key).set(value);
            return true;
        });
    }

    /**
     * 使用指定编码器设置无过期值。
     *
     * @param key 键
     * @param value 值
     * @param codec 明确的线格式
     * @param <V> 值类型
     * @return 成功时 true
     */
    public <V> Boolean set(String key, V value, Codec codec) {
        key(key);
        Assert.notNull(codec, "编码器不能为空");
        return write(client -> {
            client.getBucket(key, codec).set(value);
            return true;
        });
    }

    /**
     * 设置值及有效期，时长必须至少一毫秒。
     *
     * @param key 键
     * @param value 值
     * @param duration 有效期
     * @param <V> 值类型
     * @return 成功时 true
     */
    public <V> Boolean set(String key, V value, Duration duration) {
        key(key);
        millis(duration);
        return write(client -> {
            client.getBucket(key).set(value, duration);
            return true;
        });
    }

    /**
     * 使用指定编码器设置值及有效期。
     *
     * @param key 键
     * @param value 值
     * @param duration 有效期，至少一毫秒
     * @param codec 明确的线格式
     * @param <V> 值类型
     * @return 成功时 true
     */
    public <V> Boolean set(String key, V value, Duration duration, Codec codec) {
        key(key);
        millis(duration);
        Assert.notNull(codec, "编码器不能为空");
        return write(client -> {
            client.getBucket(key, codec).set(value, duration);
            return true;
        });
    }

    /**
     * 将对象序列化成 JSON 字符串后使用当前连接编码存储，不启用任意类型实例化。
     *
     * @param key 键
     * @param value 对象
     * @return 主库存储成功时 true
     */
    public Boolean setSerialize(String key, Object value) {
        return set(key, mapper.writeValueAsString(value));
    }

    /**
     * 仅在主库执行原子的不存在时写入，保留旧版不向备库复制 NX 的锁用途。
     *
     * @param key 键
     * @param value 值
     * @param duration 有效期，至少一毫秒
     * @param <V> 值类型
     * @return 主库新建成功时 true
     */
    public <V> boolean setNx(String key, V value, Duration duration) {
        key(key);
        millis(duration);
        return writes.write(client -> client.getBucket(key).setIfAbsent(value, duration), null);
    }

    /**
     * 用指定线格式读取主库。
     *
     * @param key 键
     * @param codec 编解码器
     * @param <V> 值类型
     * @return 值，不存在时 null
     */
    public <V> V get(String key, Codec codec) {
        key(key);
        Assert.notNull(codec, "编码器不能为空");
        return primary.<V>getBucket(key, codec).get();
    }

    /**
     * 使用连接默认线格式读取主库。
     *
     * @param key 键
     * @param <V> 值类型
     * @return 值，不存在时 null
     */
    public <V> V get(String key) {
        key(key);
        return primary.<V>getBucket(key).get();
    }

    /**
     * 将 setSerialize 写入的 JSON 字符串还原到指定类型。
     *
     * @param key 键
     * @param clazz 明确的目标类
     * @param <T> 模型类型
     * @return 模型，缺失或空字符串时 null
     * @throws Exception 保留原签名；无效 JSON 或类型不匹配时抛出
     */
    public <T> T get(String key, Class<T> clazz) throws Exception {
        Assert.notNull(clazz, "目标类型不能为空");
        String text = get(key);
        return text == null || text.isEmpty() ? null : mapper.readValue(text, clazz);
    }

    /**
     * 设置 Hash 字段。
     *
     * @param key 键
     * @param field 字段名
     * @param value 字段值
     * @param <V> 字段值类型
     */
    public <V> void hset(String key, String field, V value) {
        key(key);
        write(client -> client.<String, V>getMap(key).put(field, value));
    }

    /**
     * 批量设置 Hash 字段，调用时复制映射容器。
     *
     * @param key 键
     * @param map 字段映射
     * @param <V> 字段值类型
     */
    public <V> void hmset(String key, Map<String, V> map) {
        key(key);
        Map<String, V> values = Map.copyOf(map);
        write(client -> {
            client.<String, V>getMap(key).putAll(values);
            return true;
        });
    }

    /**
     * 获取 Hash 字段数。
     *
     * @param key 键
     * @param <V> 保留原签名的值类型
     * @return 字段数
     */
    public <V> int hgetCount(String key) {
        key(key);
        return primary.getMap(key).size();
    }

    /**
     * 获取单个 Hash 字段。
     *
     * @param key 键
     * @param field 字段名
     * @param <V> 字段值类型
     * @return 值，字段不存在时 null
     */
    public <V> V hget(String key, String field) {
        key(key);
        return primary.<String, V>getMap(key).get(field);
    }

    /**
     * 将全部 Hash 字段转换成指定模型。
     *
     * @param key 键
     * @param clazz 模型类型
     * @param <V> 模型类型
     * @return 转换后的模型，类型不匹配时明确失败
     */
    public <V> V hget(String key, Class<V> clazz) {
        return mapper.convertValue(hgetAll(key), clazz);
    }

    /**
     * 获取 Hash 的完整快照。
     *
     * @param key 键
     * @param <K> 字段名类型
     * @param <V> 字段值类型
     * @return 字段快照，键不存在时为空
     */
    public <K, V> Map<K, V> hgetAll(String key) {
        key(key);
        return primary.<K, V>getMap(key).readAllMap();
    }

    /**
     * 删除指定 Hash 字段。
     *
     * @param key 键
     * @param fields 字段名
     * @return 实际删除数量
     */
    public Long hdel(String key, String... fields) {
        key(key);
        String[] values = fields.clone();
        return values.length == 0 ? 0L : write(client -> client.getMap(key).fastRemove((Object[]) values));
    }

    /**
     * 以长整数递增 Hash 字段。
     *
     * @param key 键
     * @param field 字段
     * @param value 增量
     * @param <V> 数值返回类型
     * @return 递增后的值
     */
    public <V> V hincrby(String key, String field, Long value) {
        return hincrby(key, field, (Number) value);
    }

    /**
     * 以浮点数递增 Hash 字段。
     *
     * @param key 键
     * @param field 字段
     * @param value 有限增量
     * @param <V> 数值返回类型
     * @return 递增后的值
     */
    public <V> V hincrby(String key, String field, Double value) {
        return hincrby(key, field, (Number) value);
    }

    /**
     * 以指定数值类型递增 Hash 字段；字符串编码字段也必须存放合法数值。
     *
     * @param key 键
     * @param field 字段
     * @param value 数值增量
     * @param <V> 与增量一致的返回类型
     * @return 递增后的值
     */
    public <V> V hincrby(String key, String field, Number value) {
        key(key);
        Assert.notNull(value, "增量不能为空");
        Assert.isTrue(Double.isFinite(value.doubleValue()), "增量必须有限");
        return write(client -> client.<String, V>getMap(key).addAndGet(field, value));
    }

    /**
     * 添加集合元素。
     *
     * @param key 键
     * @param values 元素
     * @param <V> 元素类型
     * @return 集合发生变化时 true
     */
    public <V> Boolean sadd(String key, V... values) {
        return saddAll(key, Arrays.asList(values));
    }

    /**
     * 添加列表中的全部集合元素，复制列表容器。
     *
     * @param key 键
     * @param values 元素列表
     * @param <V> 元素类型
     * @return 集合发生变化时 true
     */
    public <V> Boolean saddAll(String key, List<V> values) {
        key(key);
        List<V> snapshot = List.copyOf(values);
        return !snapshot.isEmpty() && write(client -> client.<V>getSet(key).addAll(snapshot));
    }

    /**
     * 添加指定集合的全部元素。
     *
     * @param key 键
     * @param values 元素集合
     * @param <V> 元素类型
     * @return 集合发生变化时 true
     */
    public <V> Boolean saddAll(String key, Set<V> values) {
        return saddAll(key, List.copyOf(values));
    }

    /**
     * 删除集合元素。
     *
     * @param key 键
     * @param values 元素
     * @param <V> 元素类型
     * @return 集合发生变化时 true
     */
    public <V> Boolean srem(String key, V... values) {
        return sremAll(key, Arrays.asList(values));
    }

    /**
     * 删除列表指定的集合元素。
     *
     * @param key 键
     * @param values 元素列表
     * @param <V> 元素类型
     * @return 集合发生变化时 true
     */
    public <V> Boolean sremAll(String key, List<V> values) {
        key(key);
        List<V> snapshot = List.copyOf(values);
        return !snapshot.isEmpty() && write(client -> client.<V>getSet(key).removeAll(snapshot));
    }

    /**
     * 删除指定集合中的全部元素。
     *
     * @param key 键
     * @param values 元素集合
     * @param <V> 元素类型
     * @return 集合发生变化时 true
     */
    public <V> Boolean sremAll(String key, Set<V> values) {
        return sremAll(key, List.copyOf(values));
    }

    /**
     * 获取完整集合快照。
     *
     * @param key 键
     * @param <V> 元素类型
     * @return 元素集合，缺失时为空
     */
    public <V> Set<V> smembers(String key) {
        key(key);
        return primary.<V>getSet(key).readAll();
    }

    /**
     * 检查集合成员。
     *
     * @param key 键
     * @param value 元素
     * @param <V> 元素类型
     * @return 存在时 true
     */
    public <V> Boolean sismember(String key, V value) {
        key(key);
        return primary.getSet(key).contains(value);
    }

    /**
     * 只读取一个随机元素；保留旧版未使用的 value 参数，不删除元素。
     *
     * @param key 键
     * @param value 旧兼容参数，不参与选择
     * @param <V> 元素类型
     * @return 随机元素，空集合时 null
     */
    public <V> V sRandom(String key, V value) {
        key(key);
        return primary.<V>getSet(key).random();
    }

    /**
     * 在主库随机删除一个元素，备库删除同一元素，空集合不提交无效删除。
     *
     * @param key 键
     * @param <V> 元素类型
     * @return 被删除的元素，空集合时 null
     */
    public <V> V spop(String key) {
        key(key);
        return writeWithResult(client -> client.<V>getSet(key).removeRandom(), (client, value) -> {
            if (value != null) {
                client.getSet(key).remove(value);
            }
        });
    }

    /**
     * 在主库随机删除指定数量元素，备库只删除主库返回的成员。
     *
     * @param key 键
     * @param count 非负数量
     * @param <V> 元素类型
     * @return 被删除元素的集合
     */
    public <V> Set<V> spop(String key, int count) {
        key(key);
        Assert.isTrue(count >= 0, "弹出数量不能为负数");
        if (count == 0) {
            return Set.of();
        }
        return writeWithResult(client -> client.<V>getSet(key).removeRandom(count), (client, values) -> {
            if (!values.isEmpty()) {
                client.getSet(key).removeAll(values);
            }
        });
    }

    /**
     * 沿用原方法实际行为，将元素按输入顺序追加到列表尾部，不是 Redis LPUSH 的头插。
     *
     * @param key 键
     * @param values 元素
     * @param <V> 元素类型
     * @return 列表改变时 true
     */
    public <V> Boolean lpush(String key, V... values) {
        return lpushAll(key, Arrays.asList(values));
    }

    /**
     * 读取完整列表。
     *
     * @param key 键
     * @param <V> 元素类型
     * @return 列表快照，缺失时为空
     */
    public <V> List<V> lrange(String key) {
        key(key);
        return primary.<V>getList(key).readAll();
    }

    /**
     * 读取闭区间列表切片，支持 Redis 的负下标。
     *
     * @param key 键
     * @param start 起始下标，包含
     * @param end 结束下标，包含
     * @param <V> 元素类型
     * @return 指定区间元素
     */
    public <V> List<V> lrange(String key, int start, int end) {
        key(key);
        return primary.<V>getList(key).range(start, end);
    }

    /**
     * 从头删除第一个匹配的列表元素。
     *
     * @param key 键
     * @param value 元素
     * @param <V> 元素类型
     * @return 删除成功时 true
     */
    public <V> Boolean lrem(String key, V value) {
        key(key);
        return write(client -> client.getList(key).remove(value));
    }

    /**
     * 将列表元素追加到尾部。
     *
     * @param key 键
     * @param values 元素列表
     * @param <V> 元素类型
     * @return 列表改变时 true
     */
    public <V> Boolean lpushAll(String key, List<V> values) {
        key(key);
        List<V> snapshot = List.copyOf(values);
        return !snapshot.isEmpty() && write(client -> client.<V>getList(key).addAll(snapshot));
    }

    /**
     * 删除列表中所有属于指定集合的元素。
     *
     * @param key 键
     * @param values 待删除元素列表
     * @param <V> 元素类型
     * @return 列表改变时 true
     */
    public <V> Boolean lremAll(String key, List<V> values) {
        key(key);
        List<V> snapshot = List.copyOf(values);
        return !snapshot.isEmpty() && write(client -> client.getList(key).removeAll(snapshot));
    }

    /**
     * 判断列表是否包含指定元素。
     *
     * @param key 键
     * @param value 元素
     * @param <V> 元素类型
     * @return 存在时 true
     */
    public <V> Boolean lcontains(String key, V value) {
        key(key);
        return primary.getList(key).contains(value);
    }

    /**
     * 设置有序集合成员分数。
     *
     * @param key 键
     * @param value 成员
     * @param score 有限分数
     * @param <V> 成员类型
     * @return 新增成员时 true，更新已有分数时 false
     */
    public <V> Boolean zadd(String key, V value, double score) {
        key(key);
        finite(score);
        return write(client -> client.<V>getScoredSortedSet(key).add(score, value));
    }

    /**
     * 按升序获取指定闭区间分数中的成员。
     *
     * @param key 键
     * @param min 最小分数，包含，可为负无穷
     * @param max 最大分数，包含，可为正无穷
     * @param <V> 成员类型
     * @return 有序成员集合
     */
    public <V> Collection<V> zrangeByScore(String key, double min, double max) {
        key(key);
        return primary.<V>getScoredSortedSet(key).valueRange(min, true, max, true);
    }

    /**
     * 获取成员分数。
     *
     * @param key 键
     * @param value 成员
     * @param <V> 成员类型
     * @return 分数，成员不存在时 null
     */
    public <V> Double zscore(String key, V value) {
        key(key);
        return primary.getScoredSortedSet(key).getScore(value);
    }

    /**
     * 批量添加或更新成员分数，提交前验证全部分数。
     *
     * @param key 键
     * @param values 成员到有限分数的映射
     * @param <V> 成员类型
     * @return 新增成员数量
     */
    public <V> int zaddAll(String key, Map<V, Double> values) {
        key(key);
        Map<V, Double> snapshot = Map.copyOf(values);
        snapshot.values().forEach(RedissonUtil::finite);
        return snapshot.isEmpty() ? 0 : write(client -> client.<V>getScoredSortedSet(key).addAll(snapshot));
    }

    /**
     * 获取成员的降序排名。
     *
     * @param key 键
     * @param value 成员
     * @param <V> 成员类型
     * @return 从 0 开始的排名，成员缺失时 null
     */
    public <V> Integer zrevrank(String key, V value) {
        key(key);
        return primary.getScoredSortedSet(key).revRank(value);
    }

    /**
     * 获取降序排名闭区间内的成员。
     *
     * @param key 键
     * @param start 开始排名，包含
     * @param end 结束排名，包含，支持负下标
     * @param <V> 成员类型
     * @return 降序成员集合
     */
    public <V> Collection<V> zrevrange(String key, int start, int end) {
        key(key);
        return primary.<V>getScoredSortedSet(key).valueRangeReversed(start, end);
    }

    /**
     * 读取按分数升序排列的全部成员及分数。
     *
     * @param key 键
     * @param <V> 成员类型
     * @return 带分数的成员集合
     */
    public <V> Collection<ScoredEntry<V>> zgetAllWithScores(String key) {
        key(key);
        return primary.<V>getScoredSortedSet(key).entryRange(0, -1);
    }

    /**
     * 删除有序集合成员，保留原布尔返回契约。
     *
     * @param key 键
     * @param values 成员
     * @param <V> 成员类型
     * @return 集合发生变化时 true，不是删除数量
     */
    public <V> boolean zrem(String key, V... values) {
        key(key);
        List<V> snapshot = List.copyOf(Arrays.asList(values));
        return !snapshot.isEmpty() && write(client -> client.getScoredSortedSet(key).removeAll(snapshot));
    }

    /**
     * 获取有序集合大小。
     *
     * @param key 键
     * @return 成员数量
     */
    public int zcard(String key) {
        key(key);
        return primary.getScoredSortedSet(key).size();
    }

    /**
     * 统计闭区间分数内的成员。
     *
     * @param key 键
     * @param min 最小分数，包含
     * @param max 最大分数，包含
     * @return 成员数量
     */
    public int zcount(String key, double min, double max) {
        key(key);
        return primary.getScoredSortedSet(key).count(min, true, max, true);
    }

    /**
     * 递增成员分数。
     *
     * @param key 键
     * @param value 成员
     * @param delta 有限增量
     * @param <V> 成员类型
     * @return 递增后的分数
     */
    public <V> Double zincrby(String key, V value, double delta) {
        key(key);
        finite(delta);
        return write(client -> client.<V>getScoredSortedSet(key).addScore(value, delta));
    }

    /**
     * 添加双端队列尾部元素。
     *
     * @param key 键
     * @param value 元素
     * @param <V> 元素类型
     * @return 入队成功时 true
     */
    public <V> Boolean offerLast(String key, V value) {
        key(key);
        return write(client -> client.<V>getDeque(key).offerLast(value));
    }

    /**
     * 添加双端队列头部元素。
     *
     * @param key 键
     * @param value 元素
     * @param <V> 元素类型
     * @return 入队成功时 true
     */
    public <V> Boolean offerFirst(String key, V value) {
        key(key);
        return write(client -> client.<V>getDeque(key).offerFirst(value));
    }

    /**
     * 弹出队首，备库只删除从头找到的同一元素。
     *
     * @param key 键
     * @param <V> 元素类型
     * @return 队首元素，队列为空时 null
     */
    public <V> V pollFirst(String key) {
        key(key);
        return writeWithResult(client -> client.<V>getDeque(key).pollFirst(), (client, value) -> {
            if (value != null) {
                client.getDeque(key).removeFirstOccurrence(value);
            }
        });
    }

    /**
     * 弹出队尾，备库从尾部删除同一元素，避免重复值误删队首。
     *
     * @param key 键
     * @param <V> 元素类型
     * @return 队尾元素，队列为空时 null
     */
    public <V> V pollLast(String key) {
        key(key);
        return writeWithResult(client -> client.<V>getDeque(key).pollLast(), (client, value) -> {
            if (value != null) {
                client.getDeque(key).removeLastOccurrence(value);
            }
        });
    }

    /**
     * 批量追加双端队列尾部。
     *
     * @param key 键
     * @param values 元素列表
     * @param <V> 元素类型
     * @return 队列发生变化时 true
     */
    public <V> Boolean dequeAddAll(String key, List<V> values) {
        key(key);
        List<V> snapshot = List.copyOf(values);
        return !snapshot.isEmpty() && write(client -> client.<V>getDeque(key).addAll(snapshot));
    }

    /**
     * 删除双端队列中所有指定值。
     *
     * @param key 键
     * @param values 元素列表
     * @param <V> 元素类型
     * @return 队列发生变化时 true
     */
    public <V> Boolean dequeRemoveAll(String key, List<V> values) {
        key(key);
        List<V> snapshot = List.copyOf(values);
        return !snapshot.isEmpty() && write(client -> client.getDeque(key).removeAll(snapshot));
    }

    /**
     * 检查双端队列是否包含元素。
     *
     * @param key 键
     * @param value 元素
     * @param <V> 元素类型
     * @return 存在时 true
     */
    public <V> boolean containDeque(String key, V value) {
        key(key);
        return primary.getDeque(key).contains(value);
    }

    /**
     * 获取主库可重入锁，调用方负责在同一线程释放。
     *
     * @param key 锁名称
     * @return 锁对象，不向备库复制
     */
    public RLock getLock(String key) {
        key(key);
        return primary.getLock(key);
    }

    /**
     * 尝试获取主库锁；中断返回 false 且恢复中断标志。
     *
     * @param key 锁名称
     * @param waitTime 非负等待时间
     * @param leaseTime 正租约时间，-1 使用 Redisson 看门狗
     * @param unit 时间单位
     * @return 成功时 true，调用方须在同一线程释放
     */
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        Assert.notNull(unit, "时间单位不能为空");
        Assert.isTrue(waitTime >= 0 && (leaseTime > 0 || leaseTime == -1), "锁等待或租约不合法");
        try {
            return getLock(key).tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 获取主库读写锁，调用方负责释放取得的读锁或写锁。
     *
     * @param key 锁名称
     * @return 读写锁对象
     */
    public RReadWriteLock getReadWriteLock(String key) {
        key(key);
        return primary.getReadWriteLock(key);
    }

    /**
     * 获取主库公平锁，调用方负责释放。
     *
     * @param key 锁名称
     * @return 公平锁对象
     */
    public RLock getFairLock(String key) {
        key(key);
        return primary.getFairLock(key);
    }

    /**
     * 向阻塞队列入队；只有主库入队成功才复制。
     *
     * @param key 键
     * @param value 元素
     * @param <E> 元素类型
     * @return 入队结果
     */
    public <E> boolean offerBlockingQueue(String key, E value) {
        key(key);
        return writeWithResult(client -> client.<E>getBlockingQueue(key).offer(value), (client, offered) -> {
            if (offered) {
                client.getBlockingQueue(key).offer(value);
            }
        });
    }

    /**
     * 等待主库队列元素，不持主写顺序锁等待，允许其他线程正常入队。
     * 中断恢复标志并抛出异常；结果删除在消费后按顺序复制，不构成跨库消费事务。
     *
     * @param key 键
     * @param <E> 元素类型
     * @return 已取出的元素
     */
    public <E> E takeBlockingQueue(String key) {
        key(key);
        try {
            E value = primary.<E>getBlockingQueue(key).take();
            return mirrorConsumed(key, value);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redis队列等待被中断", exception);
        }
    }

    /**
     * 限时等待主库队列元素，不持写锁等待。
     *
     * @param key 键
     * @param timeout 非负等待时间
     * @param unit 时间单位
     * @param <E> 元素类型
     * @return 元素，超时时 null；中断时恢复标志并抛出异常
     */
    public <E> E pollBlockingQueue(String key, long timeout, TimeUnit unit) {
        key(key);
        Assert.isTrue(timeout >= 0, "队列等待不能为负数");
        Assert.notNull(unit, "时间单位不能为空");
        try {
            E value = primary.<E>getBlockingQueue(key).poll(timeout, unit);
            return value == null ? null : mirrorConsumed(key, value);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redis队列等待被中断", exception);
        }
    }

    /**
     * 复制主库已消费元素的删除，不能再从备库独立弹出另一元素。
     *
     * @param key 键
     * @param value 已消费元素
     * @param <E> 元素类型
     * @return 原元素
     */
    private <E> E mirrorConsumed(String key, E value) {
        return writeWithResult(client -> value, (client, consumed) -> client.getBlockingQueue(key).remove(consumed));
    }

    /**
     * 添加或更新一个地理位置，坐标范围由 Redis GEO 协议检查。
     *
     * @param key 键
     * @param longitude 经度
     * @param latitude 纬度
     * @param member 成员
     * @param <V> 成员类型
     * @return 新增位置数量
     */
    public <V> long addGeoLocation(String key, double longitude, double latitude, V member) {
        key(key);
        return write(client -> client.<V>getGeo(key).add(longitude, latitude, member));
    }

    /**
     * 批量添加地理位置。
     *
     * @param key 键
     * @param geoEntry 位置条目
     * @param <V> 保留原泛型入口
     * @return 新增数量
     */
    public <V> long addGeoLocation(String key, GeoEntry... geoEntry) {
        key(key);
        GeoEntry[] snapshot = geoEntry.clone();
        return snapshot.length == 0 ? 0 : write(client -> client.getGeo(key).add(snapshot));
    }

    /**
     * 删除地理位置成员。
     *
     * @param key 键
     * @param value 成员
     * @param <V> 成员类型
     * @return 删除成功时 true
     */
    public <V> boolean removeGeoLocation(String key, V value) {
        key(key);
        return write(client -> client.getGeo(key).remove(value));
    }

    /**
     * 批量删除地理位置成员。
     *
     * @param key 键
     * @param values 成员列表
     * @param <V> 成员类型
     * @return 集合变化时 true
     */
    public <V> boolean removeGeoLocations(String key, List<V> values) {
        key(key);
        List<V> snapshot = List.copyOf(values);
        return !snapshot.isEmpty() && write(client -> client.getGeo(key).removeAll(snapshot));
    }

    /**
     * 查询指定成员的经纬度。
     *
     * @param key 键
     * @param members 成员
     * @param <V> 成员类型
     * @return 成员到位置的映射
     */
    @SafeVarargs
    public final <V> Map<V, GeoPosition> getGeoPosition(String key, V... members) {
        key(key);
        return primary.<V>getGeo(key).pos(members);
    }

    /**
     * 计算两个成员的球面距离。
     *
     * @param key 键
     * @param firstMember 第一个成员
     * @param secondMember 第二个成员
     * @param geoUnit 距离单位
     * @param <V> 成员类型
     * @return 距离，任一成员缺失时 null
     */
    public <V> Double getDistance(String key, V firstMember, V secondMember, GeoUnit geoUnit) {
        key(key);
        return primary.getGeo(key).dist(firstMember, secondMember, geoUnit);
    }

    /**
     * 查询半径内成员，不额外规定成员顺序。
     *
     * @param key 键
     * @param longitude 中心经度
     * @param latitude 中心纬度
     * @param radius 搜索半径
     * @param unit 半径单位
     * @param <V> 成员类型
     * @return 匹配的成员列表
     */
    public <V> List<V> searchGeo(String key, double longitude, double latitude, double radius, GeoUnit unit) {
        key(key);
        return primary.<V>getGeo(key).search(GeoSearchArgs.from(longitude, latitude).radius(radius, unit));
    }

    /**
     * 查询半径内成员及距离，按距离升序。
     *
     * @param key 键
     * @param longitude 中心经度
     * @param latitude 中心纬度
     * @param radius 半径
     * @param unit 距离单位
     * @param <V> 成员类型
     * @return 成员到距离的映射
     */
    public <V> Map<V, Double> searchGeoWithDistance(String key, double longitude, double latitude,
                                                  double radius, GeoUnit unit) {
        key(key);
        return primary.<V>getGeo(key).searchWithDistance(GeoSearchArgs.from(longitude, latitude)
                .radius(radius, unit).order(GeoOrder.ASC));
    }

    /**
     * 查询半径内成员及经纬度，按距离升序。
     *
     * @param key 键
     * @param longitude 中心经度
     * @param latitude 中心纬度
     * @param radius 半径
     * @param unit 距离单位
     * @param <V> 成员类型
     * @return 成员到位置的映射
     */
    public <V> Map<V, GeoPosition> searchGeoWithPosition(String key, double longitude, double latitude,
                                                       double radius, GeoUnit unit) {
        key(key);
        return primary.<V>getGeo(key).searchWithPosition(GeoSearchArgs.from(longitude, latitude)
                .radius(radius, unit).order(GeoOrder.ASC));
    }

    /**
     * 初始化布隆过滤器，已存在时保留其配置并返回 false。
     *
     * @param key 键
     * @param expectedInsertions 正的预计元素数量
     * @param falseProbability 大于 0、小于 1 的误判率
     * @return 主库本次实际创建成功时 true
     */
    public boolean createBloomFilter(String key, long expectedInsertions, double falseProbability) {
        key(key);
        Assert.isTrue(expectedInsertions > 0 && falseProbability > 0 && falseProbability < 1,
                "布隆过滤器容量或误判率不合法");
        return write(client -> client.getBloomFilter(key).tryInit(expectedInsertions, falseProbability));
    }

    /**
     * 向已初始化布隆过滤器添加元素。
     *
     * @param key 键
     * @param value 元素
     * @param <T> 元素类型
     * @return 位集合发生变化时 true
     */
    public <T> boolean addToBloomFilter(String key, T value) {
        key(key);
        return write(client -> client.<T>getBloomFilter(key).add(value));
    }

    /**
     * 检查布隆过滤器中是否可能存在元素。
     *
     * @param key 键
     * @param value 元素
     * @param <T> 元素类型
     * @return false 表示一定不存在，true 允许按配置误判
     */
    public <T> boolean mightContainInBloomFilter(String key, T value) {
        key(key);
        return primary.<T>getBloomFilter(key).contains(value);
    }

    /**
     * 读取布隆过滤器的估计元素数量。
     *
     * @param key 键
     * @return 估计数量，不是精确集合大小
     */
    public long getBloomFilterCount(String key) {
        key(key);
        return primary.getBloomFilter(key).count();
    }

    /**
     * 获取布隆过滤器位容量。
     *
     * @param key 键
     * @return 位数，不是元素数
     */
    public long getBloomFilterSize(String key) {
        key(key);
        return primary.getBloomFilter(key).getSize();
    }

    /**
     * 获取初始化时配置的误判率。
     *
     * @param key 键
     * @return 误判率
     */
    public double getBloomFilterFalseProbability(String key) {
        key(key);
        return primary.getBloomFilter(key).getFalseProbability();
    }

    /**
     * 删除过滤器及其配置键。
     *
     * @param key 键
     * @return 主库实际删除时 true
     */
    public boolean deleteBloomFilter(String key) {
        key(key);
        return write(client -> client.getBloomFilter(key).unlink());
    }

    /**
     * 设置位图某一位。
     *
     * @param key 键
     * @param offset 非负位偏移，受 Redis 最大字符串限制
     * @param value 位值
     * @return 原位值
     */
    public Boolean setBit(String key, long offset, boolean value) {
        key(key);
        Assert.isTrue(offset >= 0, "位偏移不能为负数");
        return write(client -> client.getBitSet(key).set(offset, value));
    }

    /**
     * 获取位图某一位。
     *
     * @param key 键
     * @param offset 非负位偏移
     * @return 位值，缺失位为 false
     */
    public Boolean getBit(String key, long offset) {
        key(key);
        Assert.isTrue(offset >= 0, "位偏移不能为负数");
        return primary.getBitSet(key).get(offset);
    }

    /**
     * 统计值为 1 的位数。
     *
     * @param key 键
     * @return 置位数量
     */
    public Long bitCount(String key) {
        key(key);
        return primary.getBitSet(key).cardinality();
    }

    /**
     * 将两个源的 AND 结果覆盖目标，不将旧目标隐式当作第三个源。
     *
     * @param destKey 目标键
     * @param sourceKey1 第一源键
     * @param sourceKey2 第二源键
     * @return 成功时 true；集群要求三个键位于同一槽
     */
    public Boolean bitAnd(String destKey, String sourceKey1, String sourceKey2) {
        return bitOperation("AND", destKey, sourceKey1, sourceKey2);
    }

    /**
     * 将两个源的 OR 结果覆盖目标。
     *
     * @param destKey 目标键
     * @param sourceKey1 第一源键
     * @param sourceKey2 第二源键
     * @return 成功时 true；集群要求三个键位于同一槽
     */
    public Boolean bitOr(String destKey, String sourceKey1, String sourceKey2) {
        return bitOperation("OR", destKey, sourceKey1, sourceKey2);
    }

    /**
     * 将两个源的 XOR 结果覆盖目标。
     *
     * @param destKey 目标键
     * @param sourceKey1 第一源键
     * @param sourceKey2 第二源键
     * @return 成功时 true；集群要求三个键位于同一槽
     */
    public Boolean bitXor(String destKey, String sourceKey1, String sourceKey2) {
        return bitOperation("XOR", destKey, sourceKey1, sourceKey2);
    }

    /**
     * 清空位图。
     *
     * @param key 键
     * @return 操作成功时 true
     */
    public Boolean clearBitSet(String key) {
        key(key);
        return write(client -> {
            client.getBitSet(key).clear();
            return true;
        });
    }

    /**
     * 添加一个 HyperLogLog 元素后读取估计基数。
     *
     * @param key 键
     * @param value 元素
     * @param <T> 元素类型
     * @return 添加后的估计基数
     */
    public <T> Long pfadd(String key, T value) {
        key(key);
        return write(client -> {
            client.<T>getHyperLogLog(key).add(value);
            return client.getHyperLogLog(key).count();
        });
    }

    /**
     * 添加一批 HyperLogLog 元素后读取估计基数。
     *
     * @param key 键
     * @param values 元素集合
     * @param <T> 元素类型
     * @return 添加后的估计基数
     */
    public <T> Long pfaddAll(String key, Collection<T> values) {
        key(key);
        List<T> snapshot = List.copyOf(values);
        return write(client -> {
            if (!snapshot.isEmpty()) {
                client.<T>getHyperLogLog(key).addAll(snapshot);
            }
            return client.getHyperLogLog(key).count();
        });
    }

    /**
     * 读取 HyperLogLog 估计基数。
     *
     * @param key 键
     * @return 估计基数
     */
    public Long pfcount(String key) {
        key(key);
        return primary.getHyperLogLog(key).count();
    }

    /**
     * 读取多个 HyperLogLog 并集的估计基数。
     *
     * @param keys 源键，集群必须同槽；空集合返回 0
     * @return 并集估计基数
     */
    public Long pfcountUnion(Collection<String> keys) {
        List<String> snapshot = List.copyOf(keys);
        snapshot.forEach(RedissonUtil::key);
        if (snapshot.isEmpty()) {
            return 0L;
        }
        return primary.getHyperLogLog(snapshot.getFirst())
                .countWith(snapshot.subList(1, snapshot.size()).toArray(String[]::new));
    }

    /**
     * 将源集合合并进目标 HyperLogLog，保留 Redis PFMERGE 包含现有目标的语义。
     *
     * @param destKey 目标键
     * @param sourceKeys 非空源键集合，集群要求与目标同槽
     * @return 合并后的估计基数
     */
    public Long pfmerge(String destKey, Collection<String> sourceKeys) {
        key(destKey);
        Assert.notEmpty(sourceKeys, "HyperLogLog合并源不能为空");
        String[] snapshot = sourceKeys.toArray(String[]::new);
        Arrays.stream(snapshot).forEach(RedissonUtil::key);
        return write(client -> {
            client.getHyperLogLog(destKey).mergeWith(snapshot);
            return client.getHyperLogLog(destKey).count();
        });
    }

    /**
     * 删除 HyperLogLog。
     *
     * @param key 键
     * @return 主库实际删除时 true
     */
    public Boolean pfdelete(String key) {
        return delete(key);
    }

    /**
     * 以 1 递增并刷新滑动有效期。
     *
     * @param key 键
     * @param duration 正有效期
     * @return 递增后的精确长整数
     */
    public long increment(String key, Duration duration) {
        return increment(key, 1, duration);
    }

    /**
     * 原子递增并更新滑动有效期，不留下成功递增但未设过期的窗口。
     *
     * @param key 键
     * @param delta 长整数增量
     * @param duration 正有效期
     * @return 递增后的精确长整数，超出 Redis 有符号 64 位范围时失败
     */
    public long increment(String key, long delta, Duration duration) {
        key(key);
        long ttl = millis(duration);
        return write(client -> Long.parseLong(client.getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, INCREMENT, RScript.ReturnType.VALUE,
                        List.of(key), Long.toString(delta), Long.toString(ttl))));
    }

    /**
     * 原子浮点递增并更新滑动有效期；金额应使用业务侧整数最小单位或十进制定点协议。
     *
     * @param key 键
     * @param delta 有限增量
     * @param duration 正有效期
     * @return 递增后的值
     */
    public double incrementDouble(String key, double delta, Duration duration) {
        key(key);
        finite(delta);
        long ttl = millis(duration);
        return write(client -> Double.parseDouble(client.getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, INCREMENT_DOUBLE, RScript.ReturnType.VALUE,
                        List.of(key), Double.toString(delta), Long.toString(ttl))));
    }

    /**
     * 以 1 递减，保留现有有效期；不存在的键会创建为 -1。
     *
     * @param key 键
     * @return 递减后的值
     */
    public long decrement(String key) {
        key(key);
        return write(client -> client.getAtomicLong(key).decrementAndGet());
    }

    /**
     * 删除一个普通数据键，返回真实删除结果。
     *
     * @param key 键
     * @return 主库实际删除时 true
     */
    public boolean delete(String key) {
        key(key);
        return write(client -> client.getBucket(key).delete());
    }

    /**
     * 批量异步释放数据键；Redisson 将跨槽键分派到对应集群节点。
     *
     * @param keys 键集合，空集合不发命令
     */
    public void delete(Collection<String> keys) {
        String[] snapshot = keys.toArray(String[]::new);
        Arrays.stream(snapshot).forEach(RedissonUtil::key);
        if (snapshot.length > 0) {
            write(client -> client.getKeys().unlink(snapshot));
        }
    }

    /**
     * 设置存在键的有效期；不存在时返回 false，不伪装成功。
     *
     * @param key 键
     * @param duration 正有效期
     * @return 主库设置成功时 true
     */
    public boolean expire(String key, Duration duration) {
        key(key);
        millis(duration);
        return write(client -> client.getBucket(key).expire(duration));
    }

    /**
     * 检查主库键是否存在。
     *
     * @param key 键
     * @return 存在时 true
     */
    public boolean exists(String key) {
        key(key);
        return primary.getBucket(key).isExists();
    }

    /**
     * 获取剩余有效期毫秒。
     *
     * @param key 键
     * @return 正常为剩余毫秒，-1 表示永久，-2 表示键不存在
     */
    public long getExpire(String key) {
        key(key);
        return primary.getBucket(key).remainTimeToLive();
    }

    /**
     * 执行只包含显式两个源的原子位运算。
     *
     * @param operation 受内部调用限制的操作名称
     * @param destination 目标键
     * @param first 第一源
     * @param second 第二源
     * @return 成功时 true
     */
    private boolean bitOperation(String operation, String destination, String first, String second) {
        key(destination);
        key(first);
        key(second);
        return write(client -> {
            client.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                    "return redis.call('BITOP', ARGV[1], KEYS[1], KEYS[2], KEYS[3])",
                    RScript.ReturnType.LONG, List.of(destination, first, second), operation);
            return true;
        });
    }

    /**
     * 限时确认已接收的复制任务；任意历史备库失败都会明确抛出。
     *
     * @param timeout 等待上限
     */
    public void awaitReplication(Duration timeout) {
        writes.await(timeout);
    }

    /**
     * 获取备库执行或调度失败次数。
     *
     * @return 累计失败数
     */
    public long replicationFailures() {
        return writes.failures();
    }

    /**
     * 停止写入并等待复制，不关闭借用的客户端或执行器。
     */
    @Override
    public void close() {
        writes.close();
    }

    /**
     * 将相同确定性命令复制到备库。
     *
     * @param action 单库操作
     * @param <T> 返回类型
     * @return 主库结果
     */
    private <T> T write(Function<RedissonClient, T> action) {
        return writes.write(action, (client, ignored) -> action.apply(client));
    }

    /**
     * 将主库结果用于备库操作，避免再次随机选择元素。
     *
     * @param action 主操作
     * @param mirror 备操作
     * @param <T> 结果类型
     * @return 主库结果
     */
    private <T> T writeWithResult(Function<RedissonClient, T> action, BiConsumer<RedissonClient, T> mirror) {
        return writes.write(action, mirror);
    }

    /**
     * 校验工具键，避免无意写入空名称。
     *
     * @param key 键
     */
    private static void key(String key) {
        Assert.hasText(key, "Redis键不能为空");
    }

    /**
     * 将有效期转换成不丢失为零的毫秒值。
     *
     * @param duration 有效期
     * @return 正毫秒值
     */
    private static long millis(Duration duration) {
        Assert.notNull(duration, "有效期不能为空");
        long value = duration.toMillis();
        Assert.isTrue(value > 0, "有效期必须至少一毫秒");
        return value;
    }

    /**
     * 检查可写入的有限分数或增量。
     *
     * @param value 数值
     */
    private static void finite(double value) {
        Assert.isTrue(Double.isFinite(value), "数值必须有限");
    }
}
