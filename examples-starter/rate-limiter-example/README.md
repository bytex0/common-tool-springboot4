# 限流示例

真实集成限流、多 Redis 和 Spring Data Redis Starter，默认本机18090端口。
两种 Redis 客户端通过 TEST_REDIS_ADDRESS 指向同一专用测试服务。

- GET `/api/rate/acquire`：key、type、backend、max、window、capacity、rate、permits。
  backend 支持 REDISSON 和 REDIS_TEMPLATE；legacy=true 验证四种 Lua 的原可变参数接口。
- GET `/api/rate/annotated?key=...`：注解限流，每2秒2个许可。
- GET `/api/rate/annotated-spring?key=...`：RedisTemplate 注解路径，与上一接口共享同键额度。

仅用于测试，生产环境不得让客户端随意选择限流参数。自动化`python3 scripts/test-starter.py rate-limiter`会注入专用Redis地址并启动两个应用实例，验证配额在实例之间共享，最后清理进程与容器。
