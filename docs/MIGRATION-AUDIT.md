# 已迁移 Starter 初审与整改清单

## 审查结论

2026-10-05：此前将“简化实现的现有测试通过”写成“迁移完成”，结论不成立。
存在未经确认的功能删减、API 替换、注释缺失以及禁止使用的并发写法。
暂停新增 Starter，先按本清单复核和整改已有模块。

本次范围是根 POM 中的 **18 个 Starter**，不是只检查用户举例的两个文件。
目标版本审查基线为 `ff1b905`；参考仓库为同级 `common-tool`，
其 HEAD 为 `4e1956e4c7a4c525b18a49d02948c4bff7393e1e`。
参考仓库存在未跟踪文件，IP 数据文件也是其中之一；本次未修改参考仓库。

### 后续整改更新

2026-10-05 已完成本清单中限流模块的首轮代码整改：
恢复双后端、策略接口/工厂/具体实现、模型和异常契约、原脚本参数，修复 JavaBeans 布尔属性绑定，
并补齐注释与显式锁。全量 168 项 Java 测试、12 项限流真实检查通过。
该模块及示例已接入 Checkstyle，其他模块没有因此自动通过规范验收。
详见 [限流功能对照](../rate-limiter-spring-boot-starter/MIGRATION.md)。
以下目录数量、源码位置和 17 处监视器锁统计仍是初审基线，不代表后续修复后的当前源码。
2026-10-06 的十个模块逐项整改状态与验证证据见
[整改批次](REMEDIATION-BATCH-01.md) 及其链接的模块功能对照；未纳入该批次的缺口仍需继续处理。

2026-10-06 后续敏感词整改已通过：恢复原过滤器、分类、服务重载、动态管理和注解，
保留原结果闭区间及现有根包右开区间的各自契约，补齐原来未实现的 Web 功能。
全量 268 项 Java 测试、敏感词 12 项真实检查及 9 项 Python 测试通过；
该模块和示例已接入 Checkstyle，详细兼容变化及范围见
[敏感词功能对照](../sensitive-word-spring-boot-starter/MIGRATION.md)。
下表敏感词差异仍保留为初审证据，不再代表当前状态。

2026-10-06 多 Redis 已补齐原数据结构工具、重载、旧配置及异步双写，
并修复关闭竞争、顺序/压力控制、异常和序列化问题。全量 285 项 Java 测试、
多 Redis 32 项真实检查与锁/限流/幂等额外 37 项真实回归通过；
功能与协议边界见 [多 Redis 对照](../multi-redis-spring-boot-starter/MIGRATION.md)。
下表多 Redis 差异同样仅保留为初审基线。

本轮进行了源码目录清点、公开入口和关键实现对照、规范扫描及测试范围核查。
**这不是对全部实现的逐行正确性证明，也没有完成所有重载、参数和异常分支的等价验证。**
下列“待核对”不能被理解为“已确认没有问题”。

## 优先级

| 优先级 | 问题 | 验收要求 |
| --- | --- | --- |
| P1 | 原有成品能力被删减，只剩少量 API 或扩展点 | 按原文件、符号、配置及行为逐项补齐，或获得用户明确确认的替代方案 |
| P1 | 文档将局部测试通过标为迁移完成 | 状态拆分为功能、规范、测试、联调，不再使用笼统完成结论 |
| P2 | 注解属性无注释、枚举挤在一行、record 组件未逐项排版 | 按 AGENTS 的完整注释要求逐模块整改，保留真实语义和原 since |
| P2 | 8 个源码文件仍有 17 处 synchronized | 保持原共享状态和生命周期语义，使用合适并发工具整改并补竞争测试 |
| P2 | 缺少覆盖阿里规范和完整注释的静态门禁 | 配置支持 Java 21 的检查；人工补查自动工具不能验证的语义 |

## 模块清单

Java 文件数量只用于核实审查范围，**不能换算为功能覆盖率**。
合并实现本身不是问题，删除入口后缺少等价能力才是问题。

| Starter | 原/新主源码文件数 | 已核对的原能力与当前差异 | 当前判定 |
| --- | --- | --- | --- |
| common-tool | 40 / 6 | 原有 ID 生成、异常体系、事务辅助、操作日志、Nacos、链、负载均衡、NLP 和工具类；当前主要是基础响应、启动信息和少量辅助工具 | 基础框架已建立，基础模块功能远未完整迁移 |
| oss | 9 / 9 | 桶、对象、分片、分页和进度主体保留；原元数据更新接受 ObjectMetadata，新 API 只接收用户元数据 Map 和 contentType，不能直接指定其他标准头；OssUtil 入口及 SDK 类型适配待逐项确认 | 主体已测，元数据更新能力和 API 映射待补齐 |
| local-cache | 3 / 3 | get/put/remove/clear、加载、统计及工厂主体保留；静态工厂改实例、注册/初始化时机变化；核心缓存仍有内置监视器锁 | 核心已有测试，完整 API 对照和规范整改未完成 |
| docs | 2 / 3 | OpenAPI 元信息和认证配置主体已核对；实际访问保护有增强，UI/旧入口变化需确认 | 本轮未确认大块业务功能缺失，仍须配置和消费端等价验收 |
| excel | 13 / 2 | 原 AbstractSimpleExcelProcessor、多个导入导出 Context、异步大数据导入、进度回调及线程配置；当前只有同步模板及其配置 | 已确认功能和接入方式缩减，不能视为完整迁移 |
| i18n | 9 / 8 | 内存/资源提供器和动态消息管理主体保留；多个默认文本重载改为 getOrDefault，空语言标签行为变化 | 需补 API/默认值/回退行为对照，不能把方法重命名直接当兼容 |
| desensitize | 27 / 8 | 原 Jackson 与 Fastjson 支线、注解、处理器及工具；当前只有 Jackson 3 路径，部分类型改为统一全遮蔽 | Fastjson 接入能力缺失；策略语义需逐项确认，不能以安全为由直接取消接入需求 |
| dict | 8 / 7 | 原 @Dict 的 table/field 参数和刷新入口；当前移除注解表字段能力，用手工 JdbcDictLoader 替代 | 安全 SQL 加载有改进，但声明式能力未等价保留，且存在监视器锁 |
| multi-redis | 4 / 4 | 原 RedissonUtil 提供字符串、Hash、Set、List、ZSet、队列、Geo、位图、HyperLogLog、计数、过期及路由工具；当前主要是客户端管理器 | 工具层和路由契约没有迁入；原生 SDK 可调用不等于原工具能力已交付 |
| lock | 17 / 7 | 原策略工厂、注解和 Redis 客户端选择；新模板主要使用 Redisson，REDIS_TEMPLATE_SEMAPHORE 也走 Redisson | RedisTemplate 接入及扩展契约需补齐，不能要求原消费方自行替换后端 |
| rate-limiter | 17 / 7 | 算法枚举主体保留，但原 redisClientType 及工厂/策略扩展入口被删；注解属性无逐项说明，枚举压缩排版 | 算法已测不代表集成契约完整；功能、注释及监视器锁均需整改 |
| idempotent | 7 / 7 | 注解主体保留，处理中锁和成功窗口有改进；原 execute 检查入口改为包围业务的作用域，指纹和异常语义变化 | 不应恢复有缺陷的先检查后执行，但须提供完整迁移路径及行为对照 |
| ip2region | 4 / 4 | IPv4 查询与结果字段主体保留，资源加载有增强；DTO 改为 record，异常和未知记录语义变化，查询仍使用 synchronized | 核心查询已测，API 和规范整改未完成；不把原引擎不支持的 IPv6 算作已丢失功能 |
| sensitive-word | 14 / 3 | 原字段/方法/参数注解、分类、匹配模式、替换字符/字符串、高亮、词库加载及动态白名单；当前只有小范围服务 API | 多项功能明确缺失，匹配结束索引还由闭区间改成右开区间 |
| disruptor | 12 / 3 | 原监听注解、手工建队列/注册/关闭、等待策略、生产者类型和指标；当前固定类型化 Bean、MULTI/BLOCKING 和单平台线程 | 配置、动态队列、注解和监控未完整迁入；旧无效参数应修复而不是删除 |
| sftp | 4 / 3 | 原命名池、动态建池、池配置及借还通道入口；当前仅上传、下载、列举、删除 | 多连接管理与通道操作能力不完整；需在安全作用域内提供等价操作，不回退主机密钥校验 |
| script | 14 / 4 | 原 Groovy、JavaScript、Lua、Python、Java、编译/校验、方法执行、刷新/删除缓存；当前仅 Groovy run 和语言扩展点 | 多语言及执行/缓存管理能力明显缺失；扩展接口不是现成执行器 |
| dynamic-threadpool | 30 / 3 | 原动态队列容量、多个队列/拒绝策略、TTL、监控、告警、第三方池适配和刷新；当前主要是 core/max 调整、固定队列和快照 | 扩缩容问题已修，但大量功能未迁入；无 synchronized 不等于模块已完成 |

## 关键源码证据

原文件位于同级 `common-tool` 中相同模块的 `src/main/java/io/github/archer099` 下。
下表列出可定位的源符号和当前入口，不把 README 的自述当作唯一证据。

| 缺口 | 原文件/符号 | 当前文件/入口 |
| --- | --- | --- |
| Excel 导入进度及异步处理 | `excel/core/importer/LargeDataImportContext.java`：progressCallback、queueSize、threadCount；`LargeDataExcelImporter.importLargeExcel` | `excel-spring-boot-starter/src/main/java/io/github/bytex0/excel/ExcelTemplate.java`：read 只接受批次 consumer 和 continueOnError |
| Redis 工具层 | `redis/RedissonUtil.java`：hset、zrangeByScore、offerBlockingQueue、addGeoLocation、setBit、pfadd 等 | `multi-redis-spring-boot-starter/src/main/java/io/github/bytex0/redis/MultiRedisManager.java`：客户端选择，不是原工具方法迁移 |
| 限流后端选择 | `ratelimter/aspect/RateLimiter.java`：redisClientType；`RedisClientType.java` | `rate-limiter-spring-boot-starter/src/main/java/io/github/bytex0/ratelimiter/aspect/RateLimiter.java`：属性缺失；RateLimiterTemplate 依赖 RedissonClient |
| 字典声明式 SQL 来源 | `dict/annotation/Dict.java`：table、field | `dict-spring-boot-starter/src/main/java/io/github/bytex0/dict/annotation/Dict.java`：仅类型与后缀，手工加载器不是原注解用法 |
| 敏感词能力 | `sensitive/core/SensitiveWordFilter.java`、`handler/SensitiveWordService.java`、`annotation/*` | `sensitive-word-spring-boot-starter/src/main/java/io/github/bytex0/sensitive/SensitiveWordService.java`：缺少上述多种成品入口 |
| 动态队列和监听 | `disruptor/template/DisruptorTemplate.java`：createQueue、registerDisruptor、shutdown；`annotation/DisruptorListener.java` | `disruptor-spring-boot-starter/src/main/java/io/github/bytex0/disruptor/DisruptorTemplate.java`：构造时固定注册，无旧动态入口 |
| SFTP 命名池 | `sftp/core/JschConnectionPool.java`：buildPool、getPool、borrowObject(poolKey)、close(poolKey) | `sftp-spring-boot-starter/src/main/java/io/github/bytex0/sftp/SftpTemplate.java`：单池，execute 为私有方法 |
| 多语言脚本 | `script/executor/*Executor.java`、`service/ScriptService.java`：executeMethod、validate、refresh、remove | `script-spring-boot-starter/src/main/java/io/github/bytex0/script/ScriptService.java` 和 GroovyScriptExecutor：无对应完整能力 |
| 动态线程池扩展 | `threadpool/custom/wrapper/DynamicThreadPoolWrapper.java`、`queue/ResizableLinkedBlockingQueue.java`、`alarm/*`、`thirdparty/*` | `dynamic-threadpool-spring-boot-starter/src/main/java/io/github/bytex0/threadpool/ThreadPoolRegistry.java`：主要 submit、resize(core,max)、stats |

## 注释与阿里规范

### 已确认的违规示例

- `rate-limiter-spring-boot-starter/src/main/java/io/github/bytex0/ratelimiter/aspect/RateLimiter.java:17`：
  注解属性没有逐项 Javadoc，时间单位、适用算法、SpEL 及优先级不明确。
- `rate-limiter-spring-boot-starter/src/main/java/io/github/bytex0/ratelimiter/enums/RateLimiterType.java:10`：
  多个枚举项挤在一行，没有每项说明。
- `lock-spring-boot-starter/src/main/java/io/github/bytex0/lock/enums/LockType.java:10`：
  同样存在多枚举项同一行、逐项说明缺失。
- `dynamic-threadpool-spring-boot-starter/src/main/java/io/github/bytex0/threadpool/ThreadPoolRegistry.java:43`：
  Stats 的所有 record 组件同一行；类型级 @param 不能替代逐组件注释和排版。
- `sensitive-word-spring-boot-starter/src/main/java/io/github/bytex0/sensitive/SensitiveWordService.java:37`：
  Match 同样压缩为单行 record。
- `oss-spring-boot-starter/src/main/java/io/github/bytex0/oss/client/OssClient.java`：
  多个公开接口使用单行 Javadoc，没有完整参数、返回和异常契约。
- `rate-limiter-spring-boot-starter/src/main/java/io/github/bytex0/ratelimiter/aspect/RateLimiterAspect.java`：
  构造器同一行多语句、单行条件分支和私有方法缺失说明。
- `i18n-spring-boot-starter/src/main/java/io/github/bytex0/i18n/provider/I18nManager.java`：
  单行构造器/方法体，多个公开方法没有文档。

以上是确定的例子，不是违规文件的完整枚举。不得仅修复示例文件后宣布全仓规范通过。
根 POM 当前没有 Checkstyle/PMD 等验收门禁，release 中的 `doclint=none` 也不能检验注释完整性。
应先选用能解析 Java 21/record 的工具，再按模块清理；不能通过批量 suppression 绕过。

### synchronized 扫描

主源码中确认 8 个文件、17 处，测试与示例扫描未发现额外命中：

| 文件 | 位置 | 待保持的语义 |
| --- | --- | --- |
| `cache/core/AbstractLocalCaffeineCache.java` | 103 | 延迟初始化只执行一次及安全发布 |
| `dict/DictCache.java` | 42、54、62 | 单次加载、刷新原子性和失败保留 |
| `redis/MultiRedisManager.java` | 78 | 幂等关闭及资源所有权 |
| `lock/core/LockTemplate.java` | 137、146 | 信号量续租与释放竞争 |
| `ratelimiter/core/RateLimiterTemplate.java` | 111 | 本地计数、配额和容量回收原子性 |
| `ip2region/core/Ip2RegionTemplate.java` | 24 | Searcher 状态并发访问 |
| `sensitive/SensitiveWordService.java` | 54、58、64 | 词库增删和快照更新 |
| `disruptor/DisruptorTemplate.java` | 85、90、109、154、170 | 发布、启动和关闭竞争，不能持锁等待消费回调 |

表中路径省略各模块 `src/main/java/io/github/bytex0/` 前缀。
这些位置不能通过删除关键字或机械替换锁来修复，需逐项补并发与异常路径测试。

## 测试结论的边界

- 最近一次已有代码全量构建记录是 157 项 Java 测试通过；各已开发模块有自己的 HTTP 脚本。
- 这些测试验证的是**当前缩减后的能力集合**，没有覆盖被删除的旧入口，不能证明迁移完整。
- 模块补齐后，必须增加消费端接入/重载/配置兼容测试，并重新执行对应真实接口和受影响模块回归。
- 例如 SFTP 的当前真实联调验证四类文件操作和主机密钥，不验证多命名池；
  脚本示例只验证 Groovy，不验证 JavaScript/Lua/Python/Java；线程池示例不验证告警和第三方池适配。
- 本次提交前审查没有新增 Java 实现，也未重跑全部中间件联调；不将历史记录描述为本轮新验证结果。

## 整改次序

1. 已更新 AGENTS：完整注释、阿里规范、功能不得删减和分项完成条件；修正文档中的完成状态。
2. 从用户指出的限流模块建立完整对照样板，补全枚举、注解、模型和方法注释，同时核对后端选择、策略扩展和并发整改。
3. 按模块处理其余 17 处监视器锁及注释问题，配套测试，不能一口气机械替换。
4. 对基础模块、多 Redis、Excel、字典、脱敏和锁优先补齐原成品能力及消费端接入路径。
5. 对敏感词、Disruptor、SFTP、脚本、动态线程池逐项补齐，不用 SDK/扩展点/业务自行实现替代交付。
6. 对其余模块完成配置、默认值、重载、回调、数据格式和生命周期对照；每项绑定测试证据后才能关闭。
7. 所有相关规范与功能缺口关闭、真实联调通过后才提交对应 Starter；在此之前不继续 MQTT、Netty、XXL-JOB、Resilience4j。

## 补齐后的最低验证范围

以下用例须在实现功能对照时落实到具体测试文件和自动化步骤，不能只增加接口数量：

| 模块/能力 | 必须补充的验证 |
| --- | --- |
| 限流、锁 | RedisTemplate 与 Redisson 两种接入分别验证，覆盖所有原注解参数、策略扩展、竞争和失败释放 |
| 多 Redis | 工具方法按数据结构分组验证读写、类型、TTL、批量、路由隔离及异常；不能只测试原生 bucket 的读写 |
| Excel | 普通/多 Sheet/ZIP/异步导入导出、回调顺序及计数、失败终止/继续、资源关闭；原事务选项明确实现和边界 |
| 字典 | 声明式表字段加载、刷新、动态字典、注入攻击防护及 JSON 属性兼容 |
| 脱敏 | 各序列化接入、全部策略、嵌套模型、注解覆盖、空短字符串和 Unicode；加强策略不能无说明改变已约定输出 |
| 敏感词 | 注解参数和字段、分类、最小/最大匹配、字符/字符串替换、高亮、动态词库/白名单、原文索引及并发一致性 |
| Disruptor | 代理 Bean 监听、动态队列创建/关闭、等待和生产者策略、指标注册/清理、发布/关闭竞争及消费失败 |
| SFTP | 多池隔离、动态管理、目录/改名/属性等原通道能力、失效连接回收及主机密钥校验 |
| 脚本 | 每一种原语言的有效执行路径、参数隔离、方法调用、语法校验、源变化、缓存刷新/删除/上限、超时和关闭 |
| 动态线程池 | 动态容量和已排队任务保持、原队列/拒绝策略、上下文传播、监控/告警、第三方池适配和配置刷新 |
| OSS | 标准响应头和用户元数据更新分别验证，内容保持、分页、分片、进度及旧入口迁移 |
| 其余模块 | 对照原签名及配置验证默认值、重载、空值、异常、生命周期与消费端接入，不因初审未发现大块缺失而免检 |

## 当前交付状态

本报告初次交付为规范更新与初审，不代表全部缺陷已经修复；后续限流整改结果见文首更新。
其他模块的功能对照仍需逐项落实。保留已有效的安全修复、Boot 4 适配和测试，
不回退到原来的泄漏资源、吞异常、不安全 SQL/序列化或缺少主机密钥校验的实现。
