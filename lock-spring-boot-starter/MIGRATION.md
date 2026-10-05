# 锁功能对照

逐项阅读同级 `common-tool/lock-spring-boot-starter` 的 17 个 Java 文件、Lua 脚本、POM 与注册资源。
原项目引用 Redisson，同时声明了基于 RedisTemplate 的信号量，不能用 Redisson 模拟后者。

| 原文件及能力 | 新实现与兼容策略 | 测试证据 |
| --- | --- | --- |
| `aspect/Lock` 全部属性与原默认值 | 恢复 RedisClientType、默认 permits=10 与 defaultLockKey；新增 leaseTime | 默认值反射、静态/动态 HTTP |
| `LockAspect(ApplicationContext,LockFactory)` 与 around | 原构造恢复；完整规则优先，用户策略参与，失败不误解锁 | 自定义策略测试、动态规则 HTTP |
| `LockRule` 无参/九参数构造、Data/Builder | 可写属性和原默认 Redis 锁恢复；快照防止业务中修改影响释放 | 模型兼容、快照与关闭测试 |
| `LockFactory` 列表构造、run、lock/tryLock/unlock | 实例级不可变注册表，构造完成即可使用，内置后备及用户覆盖 | 原独立工厂、各策略真实 HTTP |
| `LockStrategy` getType/lock/tryLock/unlock | 原签名恢复；仅成功后记录句柄，释放具有线程归属 | 原策略构造与失败获取测试 |
| `ReentrantLockStrategyImpl` | 保留无参构造，活动槽引用计数，不再静态永久持有键 | 本地互斥、失败/中断清理 |
| `SemaphoreStrategyImpl` | 保留无参构造，原额度和公平模式，未获取者不增加许可 | 跨线程误释放测试、HTTP |
| `RedissonLockStrategyImpl` | 原客户端构造保留，不拥有外部客户端；支持 watchdog/显式租约 | 原构造测试、双实例 HTTP |
| `RedissonFairLockStrategyImpl` | 修复原误调用 getSpinLock，实际使用 getFairLock | 后端方法验证、双实例 HTTP |
| `RedissonSpinLockStrategyImpl` | 修复原误调用 getFairLock，实际使用 getSpinLock | 后端方法验证、双实例 HTTP |
| `RedissonSemaphoreLockStrategyImpl` | 用带期限 token 代替无归属计数，自动续租、安全释放 | 续租/释放竞争、双实例长任务 |
| `RedisTemplateSemaphoreStrategyImpl` 的构造、release | 真实 Lua token 后端，命令序列化独立，服务端时间，过期可回收 | 无 Redisson 单测、双实例长任务 |
| `LockType/RedisClientType` | 原枚举全部保留；原未实现 READ_WRITE 类型按写锁补全，并提供读模式 | 模型与读共享 HTTP |
| `LuaScriptManager` 两个原 getter | 保留入口，原计数协议升级为同槽 token 协议，新增续租脚本 | 实际 JAR 资源、RedisTemplate HTTP |
| `LockException` 八个构造器、requestId、无堆栈 | 重载保留，SLF4J 花括号格式化，原因链保留，无 synchronized | 八重载测试 |
| `LockConfiguration` 与自动发现 | Boot 4 imports，不扫描根包，策略/模板/工厂可替换，默认不连接 | 自动配置、关闭、覆盖单测及真实启动 |

## 并发与行为修正

- 作用域在准入时复制规则。原独立接口必须由同线程配对，期间不得修改规则。
- 原 lock 入口保留可中断阻塞；作用域/注解采用明确 timeout 的有界等待。
- 本地状态关闭时不直接清空，最后一个持有者/等待者离开时才移除。
- 续租任务最多受 `lock.max-scopes` 限制，取消后从队列移除。关闭拒绝新调用，保留在途续租。
- token 后端的续租不复活已释放或过期的许可，原子 ACTIVE/LOST/CLOSED 状态代替监视器。
- 续租网络失败保存原因并在释放时报错，失败或丢失锁不能撤销已经发生的业务副作用。
- 原公平/自旋实现互换、原模板阻塞直接放行、无所有权释放、静态注册表等缺陷不照搬。
- Lua 直接使用者需按 README 更新 KEYS/ARGV 协议；旧脚本缺少归属信息，不能安全混跑。
- 非精确一次执行保证：应用仍需事务、唯一约束或 fencing。异步返回后的任务不在同步锁作用域内。

## 本轮验收

2026-10-06 验证通过：

- 全量 `mvn --batch-mode --no-transfer-progress clean verify`：222 项 Java 测试通过，无失败或跳过，
  其中锁 Starter 17 项、示例 MVC 3 项。
- `python3 scripts/test-starter.py lock --skip-build`：14 项真实检查通过，两个应用进程分别执行
  本地/分布式策略、读写交叉互斥、两种信号量长任务续租、工厂原入口、动态规则和额度表达式。
- 专用 Redis、示例进程均已清理；18 个库制品和 BOM/示例依赖坐标检查通过。
- Checkstyle 已覆盖本模块、测试和示例；人工核对原入口、模型排版、字段/方法注释、
  资源归属与并发边界。没有 synchronized、Autowired 或通配符导入。

本轮原公开能力已提供兼容入口或明确的安全协议升级，功能、规范和接口验收通过。
旧 Lua 计数协议不能直接混跑，新协议及切换边界见 README。
