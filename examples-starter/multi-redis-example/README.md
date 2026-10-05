# 多 Redis 示例

真实集成命名客户端。main使用字符串与库0，secondary使用安全JSON与库1，cluster用于3主节点集群验证。

- GET `/api/redis/names`。
- PUT `/api/redis/value?client=main&key=...&ttl=30`，JSON请求体是要存的值。
- GET / DELETE 同路径，指定client和key。

自动化：`python3 scripts/test-starter.py multi-redis`。脚本自动创建隔离Redis实例、等待每个节点PING和集群就绪、注入测试地址、启动示例，再验证不同库同key隔离、JSON、集群多key路由、TTL、未知客户端拒绝和删除。最终清理应用、容器和匿名卷。

手动运行可设置TEST_REDIS_ADDRESS；集群另设TEST_REDIS_CLUSTER_ENABLED=true和逗号分隔TEST_REDIS_CLUSTER_NODES。默认仅绑定本机18088端口，示例不是生产Redis管理服务。
