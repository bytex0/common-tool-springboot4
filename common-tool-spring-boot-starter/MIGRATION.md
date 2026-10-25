# 基础 Starter 功能对照

## 范围与状态

对照来源为同级 `common-tool/common-tool-spring-boot-starter` 的实际源码、POM 和配置。
原根包 `io.github.archer099` 对应目标根包 `io.github.bytex0`。
本模块已补齐下表全部能力，原始盘点保留为历史证据，当前映射与验收见后文。
源码 API 并非二进制兼容替换：包名、实例隔离、SDK 类型与安全默认值的变化单独说明。
本模块验收不代表 Script 或动态线程池已完成，也不计入后续新增十个 Starter。

## 原功能清单

| 原文件及符号 | 当前目标位置/迁移要求 | 必须验证的行为 | 初始状态 |
| --- | --- | --- | --- |
| `CommonToolConfiguration` 两个 Bean | 同名自动配置，取消根包扫描，按需创建 ID 服务 | 默认开关、Bean 覆盖、无 Web/中间件依赖启动 | 部分已有 |
| `core/ApplicationInfoInitialize#onApplicationEvent` | 同名监听器 | 实际端口、HTTPS、上下文路径及不泄露配置 | 已有实现，需核对 |
| `common/model/ApiResponse` 全部 ok/fail 重载及 isSuccess | 同名模型 | request_id/ts 协议、所有重载、空响应、Jackson 3 | 已有实现，需逐重载核对 |
| `common/model/BaseDTO` 四个字段 | 同名 DTO，完整字段说明 | 空值、JavaBeans 与序列化 | 待补齐 |
| `exception/AuthException/BizException/ParamsException/BaseRequestException` 各八个构造器及 getRequestId | 保留类及构造入口，不照搬 synchronized | 模板参数、请求 ID、cause、无栈异常行为 | 待补齐 |
| `id/IdWorker` 构造器与 nextId | 保留原 worker/timestamp/sequence 位布局和纪元 | 并发唯一、回拨、序列用尽、时钟推进、范围校验 | 待补齐 |
| `id/IdWorkerUtil` 构造器、两个 buildIdWorker、nextId/nextIdStr | 实例持有 ID 生成器 | 不重复构造、用户 worker 配置、应用隔离 | 待补齐 |
| `transation/DoTransactionCompletion` 构造器与 afterCompletion | 保留历史包名拼写及提交回调 | 仅提交执行、回滚不执行、异常传播 | 待补齐 |
| `transation/TransactionUtils#doAfterTransaction` 两个重载 | 同步提交后回调，无事务立即执行 | 两个重载一致、嵌套事务和实际提交/回滚 | 待补齐 |
| `balancer/LoadBalancer` 两个 get | 保留契约 | 空列表行为、键空值、输入稳定性约定 | 待补齐 |
| `balancer/RandomLoadBalancer` 两个 get | 实例随机选择器 | 元素来自候选集、空列表及并发调用 | 待补齐 |
| `balancer/RoundRobinLoadBalancer` 两个 get 与 clear | 实例有界键状态，修复首次调用返回 null | 首次命中、轮询、列表变长、清空、溢出及并发 | 待补齐 |
| `chain/AbstractChainHandler` 和 Builder | 保留 doHandler、nextHandler、isEnd、addHandler/build | 处理顺序、空链、重复节点/环和构建后变更 | 待补齐 |
| `enums/BaseEnum#parseByCode/getCode/getName` | 泛型类型安全实现 | 未命中、空 code、重复编码及非枚举参数 | 待补齐 |
| `util/FunctionUtil#getCachedOrLoadDb` | 保留缓存失败回源能力 | 命中、未命中、读写失败、数据库异常和中断 | 待补齐 |
| `util/JacksonUtil` 五个入口 | Jackson 3，失败显式异常 | 泛型、空值忽略、无效 JSON、转换错误 | 待补齐 |
| `util/ValidationUtil` 四个 validate | 由实例持有 Validator，不静态获取 Spring Bean | 全对象、属性、分组、确定性错误及上下文隔离 | 待补齐 |
| `util/TraceIdUtil` 全部重载、MDC 访问及生成 | 保留 requestId 协议，修复自定义分隔符失效 | 分隔符、空值、清理、上下文快照 | 待补齐 |
| `util/Utils#format/getShardList/consumerParallel/execute/toJson/toObject` | 保留所有重载，失败不会挂起调用方 | 拒绝任务、消费异常、中断、MDC 恢复及调用方线程池所有权 | 待补齐 |
| `util/NetworkUtil` 常量和四个方法 | 保留本地地址及环境优先级，显式信任代理 | IPv4/IPv6、代理头伪造、地址获取失败 | 待补齐 |
| `common/CountDownLatch2` 构造、await 重载、countDown/getCount/reset/toString | 保留可重置能力，定义代际等待语义 | 到零后立即重置不丢唤醒、中断、超时 | 待补齐 |
| `common/ServiceThread` 全部生命周期和 protected 扩展点 | 不持锁 join，不吞中断 | 重启、并发启停、wakeup 竞争、自身关闭、超时退出 | 待补齐 |
| `controller/GitInfoController#init/getGitInfo` | 可关闭的 Web 自动配置，关闭资源流 | 无资源不失败、允许输出字段及普通非 Web 启动 | 待补齐 |
| `controller/VersionController#getVersion/ping` | 实例缓存和可选 Web 端点 | 无资源、并发读取、流关闭、开关 | 待补齐 |
| `common/log/annotation/*` 三个类型及所有属性/枚举项 | 保留日志注解及 handler/provider 配置能力 | 注解默认值、实例注入和所有操作类型 | 待补齐 |
| `common/log/model/OperationLogInfo` 全部字段 | 完整模型及链式访问器 | 日志协议、请求/响应和操作者字段 | 待补齐 |
| `common/log/service/*` 全部方法及默认提供者 | 保留可覆盖处理器与转换能力 | 默认转换不以 null 隐藏未实现、用户实例覆盖 | 待补齐 |
| `common/log/config/OperationLogAutoConfiguration` 与 aspect | Spring 7 AOP，失败不覆盖业务异常 | 请求/响应开关、敏感字段、非 Web 调用、Error 分支、处理器失败 | 待补齐 |
| `nacos/AbstractNacosConfig` 四个方法 | 直接适配 Nacos SDK，不依赖 Boot 3 专属 Starter | 各监听对象自己的服务、dataId/group 与初始加载 | 待补齐 |
| `nacos/CommonNacosConfigListener#run`、`NacosConfiguration#configService` | 显式启用、资源归属明确 | 真实隔离 Nacos 联调、注册失败回滚、更新和关闭移除 | 待补齐 |
| `nlp/DictionarySegmenter` 全部匹配/词库/枚举/评分方法 | 实例词库，快照并发读取 | 长词、Unicode、FMM/BMM/BIMM、全组合边界与评分 | 待补齐 |
| `nlp/HanlpUtil` 全部分词/提取/词库/转换方法 | 可选 HanLP 依赖，验证模型数据要求 | 实际中文分词、摘要、短语、建议、词库修改及繁简转换 | 待补齐 |

## 已识别的原实现问题

- `IdWorker` 初始化后仅递增内部时间序列，没有跟随当前时间推进；等待吞掉中断。
  `IdWorkerUtil#nextId` 在已有生成器时反而重复构造未使用的生成器。
- 轮询使用 getOrDefault/putIfAbsent 组合，首次键访问可能拿到 null；状态为无界静态共享。
- 并行消费只在成功后 countDown，异常或提交被拒绝可能使调用永久等待。
  MDC 包装无条件 clear，会清除调用者原有上下文，尤其影响直接执行和 CallerRunsPolicy。
- 事务回调两个重载对无事务处理不一致，旧注释宣称异步但实际同步执行。
- JSON 失败返回空字符串或 null；参数校验静态抓取 Spring Bean，存在初始化和多上下文问题。
- CountDownLatch 重置可能让上一代等待者再次阻塞；ServiceThread 启停状态与实际线程生命周期脱节。
- 操作日志处理器异常可能覆盖业务结果；无差别序列化请求可能泄露凭据或访问 Servlet/流对象。
- Nacos 使用 Bean 名替代 getDataId，忽略监听对象的 ConfigService，不移除监听器且打印配置正文。
- NLP 词库为静态可变集合，固定四字符限制使长词无效，全组合可能指数增长。

## 验收要求

每类能力分别更新目标符号、单元测试、示例接口和真实脚本结果。
普通构建不连接 Nacos、不下载 NLP 模型；真实外部联调单独显式运行。
无外部依赖的基础能力也必须经实际 Starter 示例调用，不能只编译或测试静态工具。
所有缺口关闭并通过完整流程后才能标记迁移完成并执行本地提交。

## 基础能力映射

上方保留初始盘点，以下表格与“本次补齐能力映射”共同覆盖该清单。
新类型及嵌套类型作者均为 bytex0，原有类型作者及创建时间未批量改写。

| 原符号 | 当前目标 | 对应验证 |
| --- | --- | --- |
| 四类请求异常各八个构造器、getRequestId | `exception/*Exception`，抽取共享请求状态，不使用内置监视器锁 | `RequestExceptionTest` 对四类逐个验证八种构造器、请求 ID、消息、cause 和堆栈 |
| `BaseDTO` 四字段 | `common/model/BaseDTO` | `ApiResponseTest#shouldPreserveBaseDtoFields` 验证四字段 JavaBeans 与 Jackson 3 协议 |
| `IdWorker`、`IdWorkerUtil` 全部入口 | `id/*`，CAS 原子更新真实时钟和序列 | `IdWorkerTest`，并发 16000 个 ID、原布局、回拨、序列耗尽、中断 |
| 基础 ID 装配及原两个无参工厂 | `CommonToolConfiguration#idWorkerUtil/applicationInfoInitialize` | `CommonToolConfigurationTest` 的无参工厂、节点值、非法范围、关闭和覆盖；HTTP 并发 400 个字符串 ID |
| 两个事务回调重载及完成回调 | `transation/*` | `TransactionUtilsTest`；示例真实 H2 提交后记录 1、回调 2，回滚后均为 0 |
| 随机/轮询两个 get 及轮询 clear | `balancer/*`，自动配置默认 Bean | `SelectionAndValidationTest` 的首次调用、原顺序、淘汰、清空、实例隔离；HTTP 单候选选择 |
| 责任链和 Builder | `chain/AbstractChainHandler` | `SelectionAndValidationTest` 的顺序、空链、重复节点及构建后修改拒绝 |
| 枚举编码查询 | `enums/BaseEnum` | `SelectionAndValidationTest` 的空值、未知和重复编码 |
| JSON 五个入口 | `util/JacksonUtil`，Jackson 3 非空值及非空 Map 内容配置 | `UtilityCompatibilityTest` 的泛型、转换、null 内容及解析失败 |
| Trace 和 MDC 全部入口 | `util/TraceIdUtil` | 单元测试及真实 HTTP 自定义分隔符、直接执行器上下文恢复 |
| 模板、分片、并行消费、execute 和 JSON 重载 | `util/Utils` | 模板不递归、分片边界、任务异常、拒绝、超时、MDC 恢复及真实 HTTP 失败后继续消费 |
| 缓存回源模板 | `util/FunctionUtil` | `UtilityCompatibilityTest` 的命中、读写故障及数据库失败 |
| 四个校验入口 | `util/ValidationUtil`、`CommonValidationAutoConfiguration` | 默认/指定组、对象/属性校验，真实 HTTP 有效和无效 JSON |
| 网络工具常量及四个入口 | `util/NetworkUtil` | `SelectionAndValidationTest` 的可信代理、防伪造；`NetworkEnvironmentTest` 使用独立 JVM 验证无环境值、空值、环境优先、IPv6 和静态常量 |
| 可重置闩锁及服务线程 | `common/CountDownLatch2`、`common/ServiceThread` | `ConcurrencyLifecycleTest` 的立即重置、提前唤醒、重复启动、重启、停机超时及子类直接释放 protected waitPoint |
| 既有响应重载 | `common/model/ApiResponse` | 保留全部重载及原有九项测试，补充 Javadoc，实际 HTTP 原协议通过 |
| 既有表达式评估与摘要 | `util/MethodExpressionEvaluator` | 补充并发 1600 个表达式的严格 512 缓存上限测试，原参数和 Bean 解析测试保留 |

## 本次补齐能力映射

以下目标均相对于 `src/main/java/io/github/bytex0/`，测试位于相同测试根包。
所有行均已通过对应单元测试及实际示例检查；独立启动开关、用户 Bean 覆盖和缺依赖场景由自动配置测试验证。

| 原文件及符号 | 目标与兼容策略 | 单元/接口测试 | 实际联调证据 |
| --- | --- | --- | --- |
| `core/ApplicationInfoInitialize#onApplicationEvent` | 同名监听器及无参构造；恢复版本、健康、监控和文档 URL，使用实际端口、IPv6、独立管理地址/协议，不打印 Nacos 连接配置 | `ApplicationInfoInitializeTest`，共享及独立管理端口、环境过滤；`CommonToolConfigurationTest` | 打包应用启动/停止及健康检查；端点 URL 不代表已授权对外公开 |
| `controller/GitInfoController#init/getGitInfo` | 同名控制器，实例缓存、有界关闭流；显式开关，默认过滤构建用户和仓库地址，include-git-details 恢复全部字段 | `MetadataControllerTest`、`CommonMetadataConfigurationTest` | `common-metadata-and-field-filtering` 对比真实资源字段 |
| `controller/VersionController#getVersion/ping` | 同名入口、无参构造；保持版本文本与 ping 协议，缺文件为 404，不静态共享缓存 | 同上，含缺失/过大资源、关闭流和缓存隔离 | 同一 HTTP 检查对比 version.json 内容和 ping 响应 |
| `common/log/annotation/EnableOperationLog` 全部属性 | 同名注解，导入 Boot 4 自动配置；Bean 优先，无参指定类仍可用，失败明确报错 | `OperationLogTest#supportsActivationModesAndAnnotationTypes/rejectsBrokenConfigurationAndHandlerCreation` | 示例通过真实注解激活，用户处理器保存本次日志 |
| `OperationLog` 全部属性、`OperationType` 七项 | 原名称、默认值及枚举保留，各项独立注释 | `OperationLogTest`、`CommonToolExampleApplicationTest` | 正常/失败业务及禁用请求响应采集对比 |
| `common/log/model/OperationLogInfo` 全部字段及访问器 | 完整模型、链式访问器；错误字段记录类型，正文有界过滤，不泄漏原异常文本 | `OperationLogTest#capturesSanitizedPayloadAndUsesUserBeans` | 实际 HTTP 对比操作者、请求、响应、时间和状态 |
| `OperationLogHandler`、`OperatorInfoProvider` 全部方法 | 同名接口，默认 convertLog 返回模型；处理器/提供者可覆盖，默认日志不输出正文 | `OperationLogTest` 的用户覆盖、失败及递归隔离 | `common-operation-log-metadata-and-masking` |
| `OperationLogAutoConfiguration`、`OperationLogAspect#around` | 保留入口；业务只执行一次，处理器失败不覆盖返回值/原异常，suppressed 保留次要故障；非 Web 可用 | `OperationLogTest` 的 Exception/Error、显式关闭、捕获上限及代理头信任 | `common-operation-log-failures-flags-and-limits`；大响应仍保留业务数据，日志使用省略标记 |
| `nacos/AbstractNacosConfig` 四方法 | 同名抽象类及同一 SDK ConfigService 契约 | `NacosListenerTest` | 实际 SDK 发布、读取、回调内容分别断言 |
| `CommonNacosConfigListener#run` 及构造器 | 使用对象自己的 service/dataId/group，最新状态串行回调；新增 close，注册失败回滚，外部客户端不由监听器关闭 | `NacosListenerTest` 的早到更新、空文本、晚到注册、重复清理异常及关闭竞争 | 四项 `common-nacos-*` 检查与容器清理 |
| `NacosConfiguration#configService` | 改为 SDK 直接创建，不复用 Boot 3 NacosConfigManager；新配置优先、旧常用配置回退，必须显式启用 | `NacosListenerTest#requiresExplicitOptInAndHonorsOverrides/assemblesSdkPropertiesWithoutLoggingSecrets` | SDK 3.1.0、隔离服务端 3.2.3；默认构建不连服务 |
| `nlp/DictionarySegmenter#initDictionary/addWord` | 实例不可变词库快照，保留追加初始化语义；不再使用静态全局词库 | `NlpTest` 的实例隔离和长词 | `common-dictionary-modes-and-combinations` |
| `forwardMaximumMatching/backwardMaximumMatching/bidirectionalMaximumMatching` | 同名算法，长词和 Unicode 码点正确处理 | `NlpTest#dictionarySupportsAllModesAndLongWords/dictionaryHandlesUnicodeAndBudgets` | 真实歧义词对比 FMM/BMM/BIMM |
| `getAllPossibleSegmentations/evaluateSegmentation/main` | 保留全组合与评分公式、演示入口；迭代遍历、有界输出，超限明确失败 | `NlpTest` 的深输入、预算和评分 | 对比组合数量与无效参数，未用截断结果伪装完整结果 |
| `HanlpUtil#indexSegment/standardSegment/standardSegmentWithNature/nlpSegment` | 使用真实 HanLP 引擎和实例词库；词语列表保持可修改，包含实体识别 | `NlpTest#hanlpPerformsSegmentationExtractionAndConversion` | `common-hanlp-real-engine-and-custom-dictionary`，无需下载模型 |
| `extractKeywords/extractSummary/extractPhrase/suggest` | 保留提取算法和单文本推荐，新增候选集合推荐；不使用占位结果 | 同上，各入口实际运行内置词典 | 对比提取数量、推荐及结果内容 |
| `addCustomDictionary` 两重载、`removeCustomDictionary/isCustomWord` | 实例词库；重复添加保留词性和频率，删除后恢复基础词典 | `NlpTest#hanlpCustomWordsAreIsolated` | 真实分词断言添加/删除与实例隔离 |
| `convertToSimplifiedChinese/convertToTraditionalChinese/main` | 原转换能力及演示入口保留 | `NlpTest` 的双向转换与空值契约 | 示例实际简繁转换内容断言 |

## 当前兼容策略

本轮源码 API 和行为变化均在 README 明确列出，不能当作无差别替换包名：

- 静态 Spring Validator 获取改为实例注入，静态轮询状态改为实例有界状态；
  保留对应业务能力，但调用方需要调整原静态调用。
- 默认客户端地址不信任代理头，显式确认可信代理后可使用代理头读取重载。
- 两个事务回调重载的无事务行为统一；回调仍为同步，不虚称异步。
- JSON 解析失败明确抛出异常，模板替换不再依赖 Map 顺序，MDC 恢复原状态。
- 责任链拒绝重复节点及构建后修改；服务线程只在旧线程实际退出后允许重启。
- 并行消费新增有限等待和明确的异常传播，不关闭调用方执行器，也不保证强杀运行中的消费动作。
- NLP 静态可变词库改为注入实例；所有匹配、提取、转换和动态词库能力保留，调用方式见 README。
- 元数据和 Nacos 由显式开关启用，避免仅引入 Starter 就公开构建信息或连接配置中心。
  旧 Nacos 专用配置对象改为 `CommonNacosProperties` 与 `Environment`，常用旧键可以回退绑定。
- 操作日志保留业务能力，默认隐藏敏感字段、不信任代理头、限制捕获大小；错误文本改为异常类型。
  日志故障与原业务异常的传播边界见 README，不把安全默认值变化隐藏为完全兼容。

## 前一阶段验证记录（dc329ad）

- `python3 scripts/test-starter.py common` 内部全量 clean verify：
  364 项 Java 测试通过，0 失败、0 错误、0 跳过。
- 基础示例真实 HTTP 9 项检查通过，包括并发 ID、参数边界、MDC 恢复、轮询/分片/模板、
  实际 JDBC 提交/回滚、校验、并行失败恢复、原响应协议以及应用启停。
- 18 个 Starter 坐标、BOM 和普通库 JAR 检查通过。
- Python 自动化自身的 9 项单元测试通过。
- 基础模块及示例已接入 Checkstyle，包含测试源码；通过不代表全部阿里规范或原功能已完成验收。
- 本轮修改了共享表达式缓存，实际回归 lock 14 项、rate-limiter 12 项、idempotent 11 项全部通过；
  对应示例进程与隔离 Redis 测试资源已由脚本清理。

## 本次验证记录（2026-10-25）

- `python3 scripts/test-starter.py common --local-nacos`：执行全量 `clean verify`，
  396 项 Java 测试通过，0 失败、0 错误、0 跳过。
- 基础示例 20 项真实 HTTP/生命周期检查全部通过，包括真实 Nacos 发布/监听/更新/删除、
  HanLP 内置引擎、操作日志正文过滤、元数据和既有基础能力。服务、容器和本次数据均已清理。
- `python3 -m unittest discover -s scripts -p 'test_*.py'`：9 项通过。
- `python3 scripts/check-coordinates.py --built-jars`：18 个 Starter、BOM、示例坐标及普通库 JAR 通过。
- 基础模块和示例 Checkstyle 通过，禁止用法扫描及 `git diff --check` 通过。
- 基于同次构建回归 lock 14 项、rate-limiter 12 项、idempotent 11 项真实检查全部通过，
  隔离 Redis 测试资源和示例进程已清理。

| 验收维度 | 当前状态 |
| --- | --- |
| 功能对齐 | 原清单能力均已映射到实现；源码 API 与安全默认值变化已说明，无以扩展点替代原成品功能 |
| 编码规范 | 模块及示例主/测试源码 Checkstyle、禁止用法扫描和本次人工复核通过 |
| 单元/接口测试 | 全量 396 项通过，无跳过；包含新增基础测试及 MockMvc 示例接口测试 |
| 真实联调 | 基础 20 项、共享能力下游 37 项通过，隔离服务和数据已清理 |

## 验收边界

- 本次能力映射是对原模块公开能力的迁移验收，不是对任意业务扩展正确性的证明。
- Nacos SDK 拒绝发布空白内容。实际脚本验证返回 400 且旧配置/回调不变；删除使用独立删除入口。
  空字符串接收仍有单元测试，不能宣称已在真实服务成功发布空字符串。
- 回调处理最新配置状态，连续快速更新可能合并中间值，不能用配置监听代替逐事件审计。
- HanLP 当前实际验证为 portable-1.8.4 内置数据，不宣称对其他外部模型或所有语言提供覆盖。
- 静态规范检查覆盖基础模块及示例全部主/测试源码；人工复核关注注释、并发、资源归属、异常与敏感数据。
  不将这次检查等同于全仓或阿里规范全部规则的自动证明。
