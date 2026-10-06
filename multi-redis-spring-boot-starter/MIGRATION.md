# 多 Redis 功能对照

参考同级 `common-tool/multi-redis-spring-boot-starter` 的四个 Java 文件、POM、
序列化示例和自动配置资源。2026-10-06 本轮原功能补齐、编码规范、测试和真实联调已验收通过。
源码清点按公开符号及重载逐项核对；工具方法没有通过删除或仅暴露底层 SDK 替代。

| 原文件及符号 | 目标实现与兼容策略 | 测试与状态 |
| --- | --- | --- |
| MultiRedisProperties、Cluster/Jedis/Pool | LegacyRedisOptions 保留原属性及默认值；RedisConfigurationSupport 绑定 spring.data.redis，显式 enabled，命名配置优先 | RedisConfigurationTest 旧配置/优先级通过；原无效字段见下文 |
| MultiRedissonConfig 三个客户端 | MultiRedisManager 统一资源所有权；保留原 Bean 名、位置路由和密码覆盖；NIO 替代强制 EPOLL | MultiRedisTest、RedisConfigurationTest；真实单机/集群路由通过 |
| MultiRedissonConfig.getCodec | RedisConfigurationSupport / ProtobufJackson3Codec 提供 STRING、JSON/Jackson 3、KRYO/KRYO5、PROTOBUF | 配置测试全部编解码/Google 标准消息通过；HTTP 标量及四类模型 Bucket/Hash 通过 |
| OtherThreadPoolConfig.otherExecutor | 有界队列、TTL 上下文传播、关闭排空；仍提供 otherRoomExecutor | RedissonUtilTest 顺序/容量/TTL 通过；示例生命周期通过 |
| RedissonUtil.initClient/getRedissonClient/getBackRedissonClient | 同名工具入口；构造即建立上下文隔离的位置路由，initClient 保留验证入口 | 配置测试 A/B/C 路由及实际主备读取通过 |
| set 四重载、setSerialize、setNx、get 三重载 | 同名方法；编码器、有效期、类型反序列化；NX 保持仅主库语义 | HTTP strings，关闭/NX 回归通过 |
| hset/hmset/hgetCount/hget 两重载/hgetAll/hdel/hincrby 三重载 | 同名方法；字段与模型转换、计数和增量 | HTTP hash，四类模型 Hash 编解码通过 |
| sadd/saddAll/srem/sremAll/smembers/sismember/sRandom/spop 两重载 | 同名方法及全部集合重载；随机弹出按主库结果复制删除 | HTTP collections 的内容、随机值、空集合与批量通过 |
| lpush/lrange 两重载/lrem/lpushAll/lremAll/lcontains | 同名方法；保留旧 lpush 尾部追加、闭区间查询 | HTTP collections 的顺序、删除和负下标通过 |
| zadd/zrangeByScore/zscore/zaddAll/zrevrank/zrevrange/zgetAllWithScores/zrem/zcard/zcount/zincrby | 同名方法；分数边界、排名与计数契约保持 | HTTP sorted 全部操作通过 |
| offerLast/offerFirst/pollFirst/pollLast/dequeAddAll/dequeRemoveAll/containDeque | 同名方法；弹出按主库结果及对应方向删除，修复重复值误删 | HTTP queues，主备内容一致通过 |
| getLock/tryLock/getReadWriteLock/getFairLock | 同名方法；锁只在主库，保留中断标志与调用方释放责任 | HTTP locks，原线程释放通过 |
| offerBlockingQueue/takeBlockingQueue/pollBlockingQueue | 同名方法；主库阻塞不持写锁等待，正确传播中断；备库不独立随机消费 | RedissonUtilTest 中断，HTTP queues 成功/超时通过 |
| addGeoLocation 两重载/removeGeoLocation/removeGeoLocations/getGeoPosition/getDistance/searchGeo 三种返回 | 同名方法；SDK 类型迁至 org.redisson.api.geo | HTTP geo 位置、距离、三种搜索和删除通过 |
| createBloomFilter/addToBloomFilter/mightContainInBloomFilter/Count/Size/FalseProbability/delete | 同名完整方法；初始化返回真实结果，读写及配置保留 | HTTP probabilistic 的重复初始化、数据和配置通过 |
| setBit/getBit/bitCount/bitAnd/bitOr/bitXor/clearBitSet | 同名方法；只计算指定源键，不能隐式混入旧目标内容 | HTTP probabilistic 单机/同槽集群三种逻辑运算通过 |
| pfadd/pfaddAll/pfcount/pfcountUnion/pfmerge/pfdelete | 同名方法；估计基数、并集、合并，空集合返回 0 | HTTP probabilistic 的基数、空集合与删除通过 |
| increment 两重载/incrementDouble/decrement/delete 两重载/expire/exists/getExpire | 同名方法；增量与有效期原子更新，不吞失败，缺失键返回真实结果 | HTTP counters，超过 2^53 的精确长整数、滑动 TTL 及删除通过 |
| write/writeWithResult | RedisWriteCoordinator 保序有界主备写，主库异常直抛，备库失败可查询且等待可报错 | RedissonUtilTest 失败/队列满/顺序/上下文/关闭，真实主备写入通过 |

表中 HTTP 分组对应示例 `RedisScenarioService`、`RedisOperationsController` 和
`scripts/test-starter.py` 的 `test_multi_redis`；九个操作分组分别在单机和三主集群执行。

## 修正和边界

- 删除监视器锁，SDK shutdown 不持有互斥锁；同一实例的不同路由只关闭一次。
- 原主库异常返回 null、异步失败无查询入口和任务乱序问题已修复；容量不足在主写前拒绝。
- 原位图运算将目标隐式作为源、尾部弹出误删重复头部值、计数与过期分离的问题已修复。
- JSON 固定值类型同时作用于 Bucket/Hash；Protobuf 库原适配器仍硬依赖 Jackson 2，
  现通过标准 Protobuf/Protostuff API 保留协议并适配 Jackson 3，不引入 Jackson 2 Databind。
- 异步双写不具备跨库事务或持久化重放；保序限于同一工具实例，阻塞消费后复制删除。
  主备 TTL 从各自执行时开始，输入对象在复制确认前应保持不可变，具体责任见 README。
- 原 `maxRedirects`、Jedis/Pool、FST 配置分支均未实际实现对应功能。
  前两项保留模型且明确不生效，FST 改为明确失败，不继续静默改成字符串。
- 包名、Redisson 4 GEO 类型、JSON 类型协议和 Kryo 注册规则不是二进制兼容替换；
  明确类型和新键空间的迁移路径见 README，不能直接用新编码器读取任意旧缓存。

## 分项验收

2026-10-06 实际结果：

- 功能对齐：上表原已实现操作及重载已恢复，未使用原生 SDK 暴露替代成品工具方法。
- 编码规范：主代码、示例和测试接入 Checkstyle；禁止用法扫描通过。
  人工检查了异常、中断、客户端所有权、双写顺序、输入与序列化边界；不宣称全仓或全部阿里规则自动通过。
- 单元/接口测试：全量 `mvn --batch-mode --no-transfer-progress clean verify` 通过，
  285 项 Java 测试，失败、错误、跳过均为 0；本 Starter 20 项，示例 MVC 4 项。
- 真实联调：最终多 Redis 32 项检查通过，包括四类模型 Bucket/Hash、九组单机/集群操作、
  主备结果、参数错误、应用停止和容器清理；全部使用隔离测试资源。
- 相关回归：锁 14 项、限流 12 项、幂等 11 项真实检查通过，合计额外 37 项。
- 工程检查：9 项 Python 测试及 18 个自有坐标/BOM/示例依赖/普通库 JAR 校验通过。

所有报告位于各示例 `target/api-test-report.json`，未纳入版本控制。测试进程、容器及匿名卷均已清理。
