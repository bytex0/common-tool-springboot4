# 多 Redis Starter

原模块实际是Redisson多集群集成。本版使用Redisson 4核心，不引入旧Boot Starter，支持命名单机和集群连接，默认不建立网络连接。

```yaml
multi-redis:
  enabled: true
  primary: main
  clients:
    main:
      mode: SINGLE
      address: redis://127.0.0.1:6379
      database: 0
      codec: STRING
    archive:
      mode: CLUSTER
      nodes:
        - redis://127.0.0.1:7000
        - redis://127.0.0.1:7001
      codec: JSON
```

通过构造器注入MultiRedisManager并调用get(name)，或注入默认RedissonClient。未知名称报错，不悄悄回退。客户端由管理器统一关闭，调用方不要单独shutdown；默认Bean关闭方法已禁用以防重复关闭。

- 先验证全部连接，再逐个创建，后续连接失败时关闭已经创建的客户端。
- 不再使用cluster/cluster2/cluster3固定字段、静态RedissonUtil路由或ThreadLocal隐式切换；迁移为显式命名连接。原SDK操作可直接在所选RedissonClient上调用。
- 支持STRING和Jackson 3 JSON，JSON为普通数据而非任意类多态反序列化。旧Kryo/FST/Protobuf静默回退不再保留，需特殊Codec时通过RedisClientFactory或原生SDK显式配置。
- 不强制EPOLL或x86原生库，默认NIO。连接参数支持ACL username/password、connect-timeout、timeout、pool-size、netty-threads。
- 密码通过外部配置传入，不放在URI里。集群逻辑库只能为0；非法模式、默认客户端缺失、非法地址都在联网前拒绝。
- 管理器按应用上下文隔离，关闭幂等。允许自定义客户端工厂和管理器，最大16个命名连接。

真实验证：`python3 scripts/test-starter.py multi-redis`。需要Docker，脚本创建自己的单机及3主节点集群并仅发布到127.0.0.1，验证后清理容器及匿名卷，不使用已有业务Redis。可通过TEST_REDIS_IMAGE替换默认redis:8-alpine镜像。
