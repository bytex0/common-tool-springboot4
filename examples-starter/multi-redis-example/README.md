# 多 Redis 示例

真实依赖 `multi-redis-spring-boot4-starter`，通过自动配置集成：
main 为字符串库 0，secondary 为普通 JSON 库 1，replica 为异步备写库 2，
kryo/kryo5/protobuf/json-typed 为库 3/4/5/6，cluster 为独立三主节点集群。

- GET `/api/redis/names`。
- PUT `/api/redis/value?client=main&key=...&ttl=30`，JSON请求体是要存的值。
- GET / DELETE 同路径，指定client和key。
- POST `/api/redis/structures/{group}?client=main&run=<UUID>`。

分组包括 strings、hash、collections、sorted、queues、geo、probabilistic、counters、locks。
每组调用恢复的 RedissonUtil 原方法，覆盖重载、边界、内容和备库结果；运行标识必须为 UUID，
所有键固定在本次运行的 `common-tool-test:{UUID}:...` 命名空间，成功或失败均尝试清理。
`typed` 分组支持 json-typed/kryo/kryo5/protobuf，分别验证 Bucket 与 Hash 的模型还原。
返回模型字段或普通 JSON 数据，不直接输出 SDK 对象。

自动化：`python3 scripts/test-starter.py multi-redis`。脚本等待 Redis 节点和应用健康就绪，
验证不同逻辑库同键隔离、JSON、集群路由、全部分组的实际业务响应、四类显式模型编解码、
双写顺序、重复元素方向、TTL、整数精度及清理。报告写入 `target/api-test-report.json`。
仅在当前源码已构建时加 `--skip-build`。普通 MVC 测试使用外部场景替身，不连接 Redis。

手动运行可设置TEST_REDIS_ADDRESS；集群另设TEST_REDIS_CLUSTER_ENABLED=true和逗号分隔TEST_REDIS_CLUSTER_NODES。默认仅绑定本机18088端口，示例不是生产Redis管理服务。
