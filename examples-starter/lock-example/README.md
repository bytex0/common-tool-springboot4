# 锁示例

真实集成锁和多Redis Starter，默认本机18089端口，测试连接由TEST_REDIS_ADDRESS注入。

- GET `/api/lock/run`：key、type、permits、wait、delay、fail参数，返回进入临界区时共享活动数。
- GET `/api/lock/annotated?key=...`：通过服务代理执行@Lock。
- GET `/api/lock/active?key=...`：同步自动化对持有状态的等待。
- GET `/api/lock/factory?key=...&type=REDISSON_LOCK`：原 LockFactory 独立获取/释放。
- GET `/api/lock/dynamic?key=...&permits=2`：Bean 方法返回完整规则，验证优先级和 RedisTemplate 后端。
- GET `/api/lock/permits?key=...&permits=2`：本地 permitsFunction 动态额度。

POM 实际依赖 `lock-spring-boot4-starter`、`multi-redis-spring-boot4-starter` 和 Boot Redis Starter。
两种 Redis 客户端使用同一个测试地址；Lettuce 命令超时为两秒。

自动化 `python3 scripts/test-starter.py lock` 会自动构建、创建专用 Redis 和两个独立进程，验证
本地/跨实例互斥、读锁共享、两种信号量的超初始租约续期、失败获取不释放、业务异常清理，
全部工厂策略、动态规则优先级、额度表达式与非法参数。最后停止进程并删除测试容器。
全量构建完成后可加 `--skip-build`，报告输出到 `target/api-test-report.json`。
