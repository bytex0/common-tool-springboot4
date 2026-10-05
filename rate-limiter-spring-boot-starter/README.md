# 限流 Starter

提供原有策略接口、工厂、具体策略、规则模型、Lua 管理和注解能力。
逐项对照及验证状态见 [功能对照](MIGRATION.md)，不以底层 SDK 代替原组件入口。

## 接入

依赖 `io.github.bytex0:rate-limiter-spring-boot-starter`，注入 `RateLimiterFactory` 调用
`tryAccess(FlowRule)`，或在 Spring 代理方法上使用 `@RateLimiter`。
`rate-limiter.enabled=false` 关闭装配，`rate-limiter.max-local-keys` 默认 10000。
LOCAL/GUAVA 不要求外部服务，库不会主动创建 Redis 连接。

- 四种 Lua 算法支持 `RedisClientType.REDISSON` 和 `RedisClientType.REDIS_TEMPLATE`。
- Redisson 后端由应用提供 `RedissonClient`；Spring 后端提供 `RedisTemplate`，
  通常由应用引入 Boot 的 `spring-boot-starter-data-redis` 配置。
- Spring 后端优先使用 `StringRedisTemplate`，其他模板存在歧义时使用 `@Primary`。
  脚本不使用业务值序列化器，两种后端使用相同 UTF-8 键/参数，指向同一 Redis 时共享额度。
- 原生 `REDISSON` 算法仍要求 RedissonClient，不伪装成 RedisTemplate 实现。

```java
@RateLimiter(
        type = RateLimiterType.REDIS_LUA_FIXED_WINDOW,
        redisClientType = RedisClientType.REDIS_TEMPLATE,
        key = "'orders:' + #request.code",
        maxRequests = 50,
        windowTime = 1
)
```

## 规则与扩展

`FlowRule` 恢复无参/全参构造、getter/setter、Builder 和原声明默认值：
启用、滑动窗口、Redisson、窗口额度 50、窗口 1 秒、桶容量 50、每秒速率 10、单次许可 1。
Builder 正确保留默认值。注解仍使用独立默认值，例如 LOCAL、窗口额度 10。

工厂保留策略列表构造器和 `run` 入口，注册在构造期完成，不使用静态注册表。
用户声明 `RateLimiterStrategy` Bean 可覆盖对应算法；重复自定义类型启动失败。
各具体策略可独立注入。底层 `RateLimiterTemplate` 只执行内置算法，不经过工厂扩展。
工厂对 null/关闭规则直接放行，底层模板要求非空规则。

字段表达式非空结果覆盖常量，完整 `ruleFunction` 结果优先。
支持 `#p0`、`#a0`、参数名、DTO 属性和 `@bean.method(...)`；禁用时不求值。
默认空 key 按方法签名隔离，表达式和额度必须来自受信业务代码，不能接受外部请求任意指定。

`AbstractRedisRateLimiterStrategy` 保留脚本访问和显式参数扩展；
覆盖 `getScript()` 对普通策略调用也生效。四种旧 Lua 参数顺序及新六参数协议均可执行，
旧客户端时间参数仍可传入，但算法改用 Redis 服务端时间。

## 边界与差异

- 七种算法均保留，补齐原缺少实现的 LOCAL，并支持加权许可。
- 窗口/桶容量最多 1000000，单次许可最多 10000，窗口最多 86400 秒；
  Guava 允许零预热，仅校验当前算法使用的字段。
- 本地使用 `ReentrantLock`，不驱逐活动额度；Guava 回收间隔涵盖许可债务。
- Lua 操作原子执行并设置 TTL；滑动窗口和桶使用 Redis 时间，漏桶拒绝也保留排水进度。
- 规则变化使用独立摘要键，旧状态按 TTL 清理，不能允许外部请求改变规则绕过限额。
- 原 `ratelimter` 包名修正为 `io.github.bytex0.ratelimiter`。
  原无参 Lua 策略和子类可由 Spring 管理，通过实例级 BeanFactoryAware 绑定模板；
  在 Spring 外手工创建时显式提供模板，不依赖全局 SpringUtil。
- 异常保留请求标识、格式化提示、cause 和业务拒绝不采集堆栈的行为。
- 旧 Redis 键/状态结构需在切换版本时安排排空或隔离，不自动删除用户业务数据。
  低层自定义脚本调用方负责键隔离及参数契约。
- Redis 管道或延迟事务执行无法即时判断额度，返回空结果时明确报错，不放行。

## 验证

在仓库根目录执行全量 `mvn --batch-mode --no-transfer-progress clean verify`，
再运行 `python3 scripts/test-starter.py rate-limiter --skip-build`。
脚本启动专用 Redis 和两个应用，验证七种算法、两后端、混合加权额度、窗口恢复、
注解、旧 Lua 参数及具体策略类，并清理所有测试资源。

库及示例在 Maven validate 接入 Checkstyle，包含测试源码，检查 Javadoc、通配符 import、
多声明、缺少大括号和 synchronized；这不是对全部阿里规范的自动化认证。
