# 幂等示例

真实集成幂等和多RedisStarter，默认本机18091端口。

- POST `/api/idempotent/run?key=...&delay=50&fail=false`：经过服务层@Idempotent，成功窗口1秒。
- GET `/api/idempotent/state?key=...`：观察共享活动数和实际业务执行次数。
- POST `/api/idempotent/reserve?key=...`：调用原独立检查，预占与 `/run` 相同的一秒窗口，不执行业务。

`python3 scripts/test-starter.py idempotent` 自动构建、创建专用 Redis 和两个应用，验证跨实例并发只执行一次、
失败可重试、处理时间超过成功窗口仍不重复、窗口结束可再次执行、空 key/非法延时拒绝，
以及独立检查与注解调用双向互斥和预占窗口过期。结束后清理自己创建的进程和容器。
已完成全量构建时可加 `--skip-build`，报告位于 `target/api-test-report.json`。

示例 POM 实际依赖 `idempotent-spring-boot4-starter` 和 `multi-redis-spring-boot4-starter`。
模拟失败发生在副作用之前，不宣称跨存储事务原子性；`reserve` 成功即占用窗口，不适用于失败自动重试业务。
