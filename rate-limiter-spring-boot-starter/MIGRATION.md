# 限流整改功能对照

参考同级 common-tool 的 `rate-limiter-spring-boot-starter`（包名 ratelimter），
以及原 examples 中的 RateLimiterController 和 RuleService。
表中区分成品能力保留、缺陷修复和接入调整，不以文件数量代替功能覆盖。

| 原入口/能力 | 当前实现 | 验证证据 |
| --- | --- | --- |
| 自动配置 | Boot 4 imports、关闭开关、Bean 覆盖、客户端按需解析 | 配置单测 |
| RateLimiterType 七项 | 全部保留并逐项注释，补 LOCAL 实现 | 策略装配检查、HTTP 七算法 |
| RedisClientType 两项 | 两后端均可独立使用，不静默回退 | 模板单测、混合后端 HTTP |
| RateLimiter 全部属性 | 恢复 redisClientType 和原数值/表达式配置 | AOP 全属性测试 |
| 参数名、DTO 属性、Bean SpEL | 保留原示例语法，增加 #p0/#a0 | AOP DTO 属性及规则提供器测试 |
| 完整规则及字段优先级 | ruleFunction 优先，字段 null 时回退常量 | AOP 覆盖测试 |
| 禁用及拒绝行为 | 禁用不求值表达式，拒绝不调用业务 | AOP 调用计数测试 |
| FlowRule 九个字段及模型契约 | 恢复无参/全参、setter/getter、equals/hashCode/toString、Builder 和原声明默认值 | 模型契约及 setter 规则提供器 |
| Builder 默认值缺失 | Builder.Default 避免 enable 变 null 无意放行 | 模型单测 |
| RateLimiterStrategy | getType/tryAccess 保留，自定义 Bean 覆盖对应算法 | 工厂覆盖和重复类型单测 |
| RateLimiterFactory(List)、run、tryAccess | 旧构造及回调保留；注册提前；null/关闭规则放行 | 工厂兼容及隔离测试 |
| 原六种具体策略类 | 同名实现恢复并可独立注入，补第七种 LOCAL | 策略装配及 HTTP |
| 原 Guava/Redisson 构造方式 | 保留 Guava 无参和直接 RedissonClient 构造 | 编译契约及对应算法执行 |
| 原 Lua 无参类和子类 | 当前容器绑定模板，不依赖全局 SpringUtil | 无参子类初始化测试 |
| getScript 子类覆盖 | 普通调用与显式参数调用都使用当前脚本 | 自定义脚本策略测试 |
| LuaScriptManager 四个 getter | 原入口保留，资源只读且随库打包 | 两后端四脚本执行 |
| 旧 Lua 参数协议 | 固定窗口 2、滑动窗口 4、令牌桶 3、漏桶 4 参数保留 | legacy=true 双后端 HTTP |
| RedisTemplate 值序列化 | 原生 UTF-8 脚本参数，不使用业务 JDK/JSON 值编码 | JDK 模板回调单测 |
| 本地窗口 | 原子加权额度、过期、容量和异常释放 | 并发、时钟和容量单测 |
| Guava | 零预热、速率、加权及状态回收 | 零预热单测、HTTP |
| Redisson 原生策略 | 分布式额度和规则更新，补充 TTL | 跨实例原生限流 HTTP |
| Lua 四算法 | 原子额度、加权、TTL、服务端时钟、漏桶修复 | 各算法及恢复 HTTP |
| RateLimitException | 八种构造、请求标识、占位符、cause、无堆栈业务拒绝 | 异常契约及 MVC 429 |
| 规范 | 注解/枚举/模型/方法注释、显式锁及模块静态门禁 | Checkstyle、源码检查 |

## 需明确的调整

1. 根包和 ratelimter 拼写修正，导入路径需调整；不宣称二进制兼容。
2. 默认注解键不再执行原来的无效 SpEL 字符串，改为按方法隔离。
3. 策略实例不共享全局 SpringUtil。无参 Lua 策略必须交给 Spring 管理，
   容器外直接创建时显式传入模板。旧跨上下文全局隐式访问不作为安全行为保留。
4. 旧脚本时间参数仍可输入，但使用 Redis 服务端时间；键和状态结构升级需部署侧规划隔离/排空。
5. 增加资源上限，Guava 允许零预热，未使用的配置字段不要求有效。
6. 规则改变创建独立状态，规则来源必须受信；不能接受用户任意配置造成额度绕过。
7. 工厂保留 null 规则放行，底层模板要求非空；低层脚本调用不能用于管道/延迟执行的即时判断。

## 验收记录

2026-10-05 本轮结果：

- 全量 `mvn --batch-mode --no-transfer-progress clean verify` 成功，168 项 Java 测试零失败、零跳过。
- 限流库和对应示例在 validate 阶段通过 Checkstyle，包含主代码与测试代码。
- `python3 scripts/test-starter.py rate-limiter --skip-build` 的 12 项真实检查全部通过。
- 示例实际依赖 Starter，使用独立 Redis 和两个应用进程，覆盖七算法、混合后端、原 Lua 参数、注解和资源清理。
- JavaBeans 内省回归确认 enable 保持 Boolean 类型及可写 setter，避免新增判断方法破坏原模型绑定。
- 本模块和示例未发现 synchronized、@Synchronized 或通配符 import。

本轮完成限流审查中已确认的功能缺口和注释/并发整改，不代表其他 Starter 已完成，
也不把已覆盖的静态规则等同于全部阿里规范的自动认证。部署时仍须处理上述导入路径及 Redis 状态版本差异。
