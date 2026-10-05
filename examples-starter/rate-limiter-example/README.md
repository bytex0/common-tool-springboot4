# 限流示例

真实集成限流和多RedisStarter，默认本机18090端口。

- GET `/api/rate/acquire`：key、type、max、window、capacity、rate、permits。
- GET `/api/rate/annotated?key=...`：注解限流，每2秒2个许可。

仅用于测试，生产环境不得让客户端随意选择限流参数。自动化`python3 scripts/test-starter.py rate-limiter`会注入专用Redis地址并启动两个应用实例，验证配额在实例之间共享，最后清理进程与容器。
