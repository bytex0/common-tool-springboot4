# 幂等示例

真实集成幂等和多RedisStarter，默认本机18091端口。

- POST `/api/idempotent/run?key=...&delay=50&fail=false`：经过服务层@Idempotent，成功窗口1秒。
- GET `/api/idempotent/state?key=...`：观察共享活动数和实际业务执行次数。

`python3 scripts/test-starter.py idempotent`自动创建专用Redis和两个应用，验证跨实例并发只执行一次、失败可重试、处理时间超过成功窗口仍不重复、窗口结束可再次执行、空key拒绝，并清理进程和容器。模拟失败发生在副作用之前，不宣称跨存储事务原子性。
