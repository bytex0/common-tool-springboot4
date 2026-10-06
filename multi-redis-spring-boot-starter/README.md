# 多 Redis Starter

坐标：`io.github.bytex0:multi-redis-spring-boot4-starter`。
使用 Redisson 4.8.0 核心、Spring Boot 4 和 Jackson 3，不引入旧 Boot Starter，
默认不建立网络连接。原 `RedissonUtil` 的全部操作和重载已恢复，
逐项证据及验收状态见 [功能对照](MIGRATION.md)。

```yaml
multi-redis:
  enabled: true
  primary: main
  backup: archive
  replication-queue-capacity: 200
  replication-timeout: 10s
  clients:
    main:
      mode: SINGLE
      address: redis://127.0.0.1:6379
      database: 0
      codec: STRING
      location: A
    archive:
      mode: CLUSTER
      nodes:
        - redis://127.0.0.1:7000
        - redis://127.0.0.1:7001
      codec: STRING
      location: B
```

通过构造器注入 `RedissonUtil` 使用完整工具，或注入 `MultiRedisManager` 显式选择连接。
`getRedissonClient("A")` 支持机房别名，也支持命名连接；未知名称报错，不悄悄回退。
`getBackRedissonClient()` 在没有配置备库时返回 null。客户端由管理器统一关闭，
调用方不要单独 shutdown；兼容客户端 Bean 的销毁方法已禁用，避免重复关闭。

## 工具能力

- 字符串：`set` 四种重载、`setSerialize`、`setNx` 和 `get` 三种重载。
- Hash：字段/批量读写、字段数量、模型转换、删除和三种 `hincrby`。
- Set/List/ZSet：原单条和集合重载、随机取出、列表区间、分数/排名/区间/计数。
- 队列：双端队列、批量操作、阻塞 take 和限时 poll；锁：可重入、公平、读写和限时获取。
- GEO：批量写入、位置、距离和三种半径搜索结果。
- 布隆过滤器：初始化、添加、查询、估计数量、容量、误判率和删除。
- 位图：读写、计数、AND/OR/XOR 和清空；HyperLogLog：添加、基数、并集、合并和删除。
- 计数器：原子整数/浮点增量及滑动 TTL、递减；通用存在检查、删除、有效期和剩余时间。

`lpush` 沿用旧方法实际的**尾部追加**行为；`lrange` 和有序集合区间包含终点。
`getExpire` 的单位是毫秒，-1 表示永久、-2 表示不存在。
位运算只使用指定的两个源，不再隐式混入旧目标；集群多键运算要求使用同一 hash tag。
`delete`、`expire` 和布隆初始化现在返回实际执行结果，不再无条件返回 true。
原吞掉的主库异常改为直接传播，阻塞操作中断保留中断标志。

## 双写和生命周期

- 未配置 `backup` 时只操作主库。配置后同步主写、异步备写，读取、NX 和锁仍只访问主库。
- 默认工具实例内的普通写操作和备写入队保序。为避免乱序，主库命令在限时获取的显式锁内执行；
  SDK 命令受响应和重试超时约束，读取不经过此锁。没有使用 `synchronized`。
- 在主写前预留复制容量，队列满时拒绝，不执行主写。每次提交的 TTL 上下文在执行后恢复。
- `awaitReplication(Duration)` 等待此前已接收任务。备库失败累计于 `replicationFailures()`，
  等待入口持续报告首次失败，后续任务仍能执行。失败不会自动清零或悄悄重试非幂等操作。
- **不是跨库事务，也不是持久化复制日志**：主库成功后仍可能备库失败、进程退出或网络不确定。
  调用方必须监控失败，按业务进行对账。异步 TTL 从各库实际执行时开始，可能存在复制延迟差。
- 保序范围不包括其他工具实例或直接 SDK 写入。阻塞消费不持写锁等待，以免阻塞生产者；
  消费后复制同一元素的删除，不提供跨库原子消费。
- 输入对象及集合元素在复制确认前保持不可变；批量容器在调用时复制。
- 工具关闭停止接收并限时排空，不释放借用的客户端或执行器。调用方应取消自己启动的长期阻塞读取。
  默认 `otherRoomExecutor` 有界、保留原 Bean 名，关闭有等待上限；替换时须自行管理其生命周期。
- 管理器按应用上下文隔离，先预校验全部连接再联网。创建失败清理已建实例；
  关闭按对象身份去重，原子开始且不持锁调用 SDK；并发/重入 close 立即返回。

## 旧配置兼容

仍可保留 `spring.data.redis` 下原 `cluster/cluster2/cluster3` 配置，
但必须额外显式开启 `multi-redis.enabled=true`。也可将旧模型放在 `multi-redis` 前缀下。
优先级为：非空 `multi-redis.clients` 整体覆盖旧连接配置；
否则使用 `multi-redis.cluster.nodes`，再回退 `spring.data.redis`。

- 主集群映射为 `primary`，默认 main；第二、第三集群映射为 secondary、third。
- 第二/第三集群仍由原 `active` 启用，主集群沿用旧行为，不检查其 `active`。
- 第二集群默认作为备写目标，第三集群保留独立路由，不擅自增加双写副作用。
- 保留 `redissonClient`、`redissonClient2`、`redissonClient3` Bean 名和原属性访问器。
  主、备及第三客户端均由管理器管理，不维护静态路由表。
- 保留集群密码覆盖公共密码，以及原连接/响应/空闲超时、重试、主从池、
  读模式、拓扑扫描、槽位覆盖、锁同步配置和默认值。
- `maxRedirects`、Jedis/Pool 原本未被 Redisson 配置使用，现保留模型而明确标为未生效；
  Redisson 自行处理 MOVED/ASK，不能把这些原无效字段描述成已实现的控制能力。
- 默认 NIO，取消按操作系统强制加载 EPOLL/x86 原生库。
- 自定义 `RedissonClient` 时默认基础设施整体退让，不偷偷再创建一组连接。
  可分别覆盖 `RedisClientFactory`、`MultiRedisManager`、`RedissonUtil` 和执行器。

## 编码与安全

| codec | 类型与协议 |
| --- | --- |
| STRING | 原字符串协议，默认选项 |
| JSON | Jackson 3，无默认多态类信息；默认普通数据，可配置 `value-type` 还原 Bucket/Hash 模型 |
| KRYO | 原 Kryo 编码器，基础标量/字节数组默认注册，业务模型需 `allowed-types` |
| KRYO5 | 原 Kryo5 编码器，明确的允许类型集合，禁止默认为任意类反序列化 |
| PROTOBUF | 必须配置 `value-type`；Google MessageLite 使用标准 Protobuf，普通 POJO 使用原 Protostuff 协议；JDK 标量显式使用 Jackson 3 |

Protobuf 的 Boot 4 适配器使用标准 Protobuf/Protostuff 库，不引用 Redisson 原实现中的
Jackson 2 工厂。消费方选用此能力时需添加 `protobuf-java`、`protostuff-core` 和
`protostuff-runtime`；不使用时无需这些可选依赖。record 不适用于本 POJO Protostuff 路径。
原 FST 配置分支实际没有实现且静默使用 String；新版明确拒绝 FST/未知类型，不假称迁移了不存在的编码器。

JSON 多态格式、Kryo 注册表和 SDK 类型发生迁移变化，**不是既有缓存字节的无缝替换**。
部署时使用新键空间或离线转码；固定类型/允许列表应在写入前确定，不随意更改。
GEO 类型使用 Redisson 4 的 `org.redisson.api.geo` 包，调用方同步调整 import。
配置类用于 Spring 绑定，手工集成应构造管理器和工具，不调用旧自动配置工厂来绕过生命周期。

密码只通过外部配置传入，不放在 URI 里。二进制模型和数据源必须可信；
不要把“固定类型”当作恶意输入沙箱。主备目标必须是不同数据空间，不能指向同一个逻辑库。

## 验证

`python3 scripts/test-starter.py multi-redis` 自动构建示例、创建隔离单机及三主集群，
仅发布到 127.0.0.1，完成真实接口及内容断言后清理进程、容器和匿名卷。
可通过 `TEST_REDIS_IMAGE` 替换默认 `redis:8-alpine`。普通 Maven 测试不依赖 Docker。
接口清单见 [示例](../examples-starter/multi-redis-example/README.md)，最新测试数量见功能对照。
