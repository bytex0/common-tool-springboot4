# 幂等功能对照

参考同级 `common-tool/idempotent-spring-boot-starter` 的 7 个 Java 文件、POM 与自动配置资源。
Java 包改为 `io.github.bytex0`，发布坐标为 `idempotent-spring-boot4-starter`。

| 原文件及能力 | 当前实现与兼容策略 | 验证 |
| --- | --- | --- |
| `aspect/Idempotent` 的 key、前缀、expire | 全部保留，恢复原默认值；空前缀/非正过期显式回落配置 | `IdempotentTest` 注解反射，双实例 HTTP |
| `IdempotentAspect` 的 ApplicationContext 构造、checkIdempotent | 同名重载保留；默认现代构造使用当前工厂和稳定键 | 原配置工厂调用与切面测试 |
| 原 POST/PUT 限定 | 扩展为任意经 Spring 代理调用的同步方法，保留 HTTP 业务使用能力 | 示例独立服务方法，真实 HTTP |
| `IdempotentProperties` 的 Boolean 开关、前缀、Duration、debugLog 与访问器 | 全部保留；日志去除键及参数，Boolean 原访问器可绑定 | 属性绑定、默认窗口测试 |
| `IdempotentKeyGenerator` 无参构造、四参数 generateKey | 原 SpEL/Bean 解析/MD5 拼接协议保留；新默认采用稳定 JSON/SHA-256 | 已知 hello MD5、原参数拼接、Bean 表达式测试 |
| 自定义生成器子类扩展 | 原重载覆盖继续生效；新重载覆盖优先；所属容器通过生命周期绑定 | `IdempotentExtensionTest` 四项测试 |
| `RedisIdempotentExecutor` 直接客户端构造、execute(String,long) | 原预占入口保留，默认时长回落配置；与本版本作用域完成标记协调 | 原键已占、默认窗口、双向互斥 HTTP |
| `IdempotentException` 四构造器与 requestId/args | 恢复并防御性复制数组；保持无堆栈，不使用 synchronized | 异常重载和数组隔离测试 |
| `IdempotentConfiguration` 三个原工厂方法 | 原方法签名继续可直接调用；Bean 注册采用按需客户端且可覆盖 | 开关、用户覆盖、原工厂调用测试 |
| 自动配置与 POM | Boot 4 imports、AspectJ、Jackson 3；没有根包扫描或强制连接 Redis | 完整 reactor 与实际示例启动 |

## 优化与边界

- 业务处理锁与完成窗口分离，使用 Redisson watchdog；成功后记标记，失败释放后可重试。
- 重复获取、同线程嵌套、处理中断和所有权丢失均单独测试，不能解锁其他请求。
- 原独立检查预占成功不代表后续业务成功，不将它伪装成事务作用域。
- 原默认 `toString`/MD5 协议仅用于兼容入口；默认新键协议不受 Map 插入顺序影响。
- 未引入静态容器，默认键映射器不加载脱敏/字典出站配置。
- 新旧版本二进制不能透明混跑，切换步骤及 Redis 写入失败边界见 README。
- 方法返回后的异步任务、跨 Redis/数据库事务、业务已提交后的网络故障不属于 exactly-once 保证。

## 验证记录

2026-10-06 本轮验证：

- 全量 `mvn --batch-mode --no-transfer-progress clean verify`：207 项 Java 测试通过，无失败或跳过；
  其中幂等 Starter 20 项、示例 MVC 3 项。
- `python3 scripts/test-starter.py idempotent --skip-build`：11 项真实检查通过，含两个应用进程、
  专用 Redis 就绪与清理、并发单次执行、失败重试、长任务、输入校验及两种入口窗口协调。
- 18 个库 JAR、BOM/示例坐标检查通过，坐标检查器 6 项 Python 测试通过。
- Checkstyle 覆盖本模块主代码、测试和示例；人工同时核对资源所有权、注释语义与原入口，
  无 synchronized、字段全限定类型或 Autowired。静态规则不代表阿里规范全部条目已自动化。

本模块原公开能力对齐与本轮验收已完成；滚动升级和分布式事务边界仍须遵守 README。
