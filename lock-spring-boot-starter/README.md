# 锁 Starter

坐标：`io.github.bytex0:lock-spring-boot4-starter`。

提供`@Lock`和`LockTemplate.execute(LockRule, action)`，默认启用，本地策略不需要Redis。通过`lock.enabled=false`关闭自动配置。使用Spring代理调用注解方法，自调用和非代理静态方法不生效。

支持本地可重入锁/公平信号量，Redisson 互斥、公平、spin、读、写锁以及可过期信号量。
`REDISSON_READ_WRITE_LOCK` 按写锁处理；`REDIS_TEMPLATE_SEMAPHORE` 使用真实 RedisTemplate Lua 后端，
无需 Redisson。`REDISSON_SEMAPHORE` 也可用 `redisClientType=REDIS_TEMPLATE` 选择该后端。
两种信号量后端使用不同存储协议，不共享容量；同一业务应统一后端。
Redisson 锁需要应用提供 RedissonClient，可配合 `multi-redis-spring-boot4-starter`。
RedisTemplate 后端优先选择 StringRedisTemplate，否则使用唯一/Primary RedisTemplate，
脚本通过原始连接使用 UTF-8 协议，不依赖模板默认对象序列化器。

- 只在成功获取后释放，业务异常保留原异常并附加清理异常。
- 本地注册表对持有者和等待者计数，最后一个引用离开即清理，不静态持有历史key。
- block=true最多等待timeout，false立即尝试；timeout明确是等待时间，不是租约。
- Redis锁leaseTime=0使用watchdog；显式租约丢失后报错，不错误解锁其他持有者。
- 两种 Redis 信号量都使用唯一许可 ID 和自动续租；额度冲突报错，失败获取不会增加许可数。
  RedisTemplate 通过服务端时间回收过期许可，续租不得复活已释放/过期许可。状态最长空闲三天后回收，
  避免短租约请求缩短其他持有者的状态 TTL。客户端需配置明确的连接及命令超时。
- 原 LockRule 无参/九参数构造、可写属性、Boolean 访问器和 builder 保留。模型默认锁为原来的
  REDISSON_LOCK；注解默认 REENTRANT_LOCK、permits=10、key="defaultLockKey"。
  默认 key 按字面值处理，修复旧默认值被当成错误 SpEL 的问题。
- 支持可信 key/permitsFunction/ruleFunction SpEL、#p0/#a0 和参数名。完整规则优先于全部注解属性，
  null 结果才回落注解；空 key 表达式用方法签名，其他字符串字面量需加 SpEL 引号。
- 不接受用户输入作为表达式，参数值不会被当作表达式再次执行。Redis key对业务键做SHA-256，不日志输出原始键。
- 分布式锁不能替代数据库唯一约束、事务或fencing；网络分区、进程暂停和租约丢失仍须业务层处理。模板是同步作用域，不用于返回后仍继续执行的异步任务。

## 原接口与生命周期

- 保留 `LockFactory(List<LockStrategy>)`、`lock/tryLock/unlock/run`、7 个具体策略类及原构造器。
  用户策略和子类优先于内置实现，重复自定义类型报错，注册表按容器隔离。
- 原 `lock` 仍可中断地无限等待；`tryLock` 按 timeout/timeUnit 限时等待，返回 false 后不得调用 unlock。
  新 `execute` 作用域的 block=true 最多等待 timeout，false 立即尝试。注解使用有界等待。
- 原切面 `(ApplicationContext, LockFactory)` 构造器保留；注解也经过工厂，用户策略不会被绕过。
- 原 `RedisTemplateSemaphoreStrategyImpl.release` 保留为所有权安全的释放入口。
  原 LuaScriptManager 的两个 getter 保留，但直接执行脚本的调用方必须升级协议：
  KEYS 是同槽的凭证有序集合和容量键，ARGV 是 token、容量、租约毫秒数。旧无凭证计数脚本不可继续混用。
- 程序化使用推荐 `execute` 或 `LockHandle` 的 try-with-resources；句柄只允许获取线程关闭。
  原独立接口也只记录成功获取，获取与释放期间不得修改规则或跨线程传递。
- `lock.max-scopes` 默认 4096，限制当前模板的持有者、等待者和续租任务，超额明确拒绝。
  关闭模板拒绝新作用域，但在途作用域继续续租和释放；最后一个退出后关闭自己的线程池。
  共享 Redis 客户端不由本组件关闭，独立构造的策略需要调用其 close。

锁状态命名空间及信号量协议已变化。旧二进制不认识新凭证，升级应停止旧流量并排空持有者后再切换，
不能宣称滚动混跑仍互斥。相同业务键的不同锁类型也不能随意互换，读/写锁例外：二者共享读写域。
详细对照与测试记录见 [MIGRATION.md](MIGRATION.md)。

验证：`python3 scripts/test-starter.py lock`，创建专用Redis及两个示例进程做跨实例并发检查后全部清理。
