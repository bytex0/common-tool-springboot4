# 限流 Starter

提供`RateLimiterTemplate.tryAccess(FlowRule)`及服务层`@RateLimiter`。修正旧包名拼写，使用`io.github.bytex0.ratelimiter`。`rate-limiter.enabled=false`关闭自动配置。

策略包括LOCAL固定窗口、GUAVA带预热速率、REDISSON共享限流及Redis Lua固定窗口、滑动窗口、令牌桶、漏桶。分布式策略需要RedissonClient，本地策略不因引入Starter就连接Redis。

- 支持加权permits，验证容量、窗口及速率；Builder默认值明确保留。
- Lua使用Redis TIME，不接受不同应用机器的时间戳。滑动窗口和桶状态有TTL，拒绝请求不会无限堆积记录。
- 修复漏桶拒绝时只更新时间却不保存排水后的水量，持续压力下仍能正常排水恢复。
- 本地状态按应用实例隔离，默认最多10000键，`rate-limiter.max-local-keys`可调整；满时仅清理已闲置的状态，不驱逐活动限额来放行。
- 规则类型和有效配置参与物理键计算。更改规则会创建独立窗口/桶；限额必须由可信服务端决定，不能让用户通过修改参数重置额度。
- 注解支持key、各数值Function及ruleFunction，使用可信SpEL；空key按方法隔离。业务键应来自可信身份，不应用任意随机请求ID规避限额。
- 原RedisTemplate分支统一到Redisson脚本执行，移除静态工厂和无界历史限流器Map。
- 这是请求准入限流，不是消息队列、并发锁或持久化账本；超额抛RateLimitException，示例映射为429。

验证：`python3 scripts/test-starter.py rate-limiter`，自动创建Redis及两个进程，检查共享配额、七种策略、加权许可、窗口恢复、持续拒绝下的漏桶排水、注解和参数校验。
