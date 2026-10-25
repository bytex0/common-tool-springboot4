# 基础工具 Starter

坐标：`io.github.bytex0:common-tool-spring-boot4-starter`。
基线为 Java 21、Spring Boot 4，JSON 工具使用 Jackson 3。
原功能逐项对照、兼容调整和实际验证记录见 [MIGRATION.md](MIGRATION.md)。

## 当前能力

- `ApiResponse`：保留全部成功和失败工厂重载、request_id/ts 字段协议以及成功判断。
- `BaseDTO`：创建人、更新人和毫秒审计时间。
- `AuthException`、`BizException`、`ParamsException`、`BaseRequestException`：
  保留八种构造器、请求标识、原因链和模板消息，不采集调用栈。
- `IdWorker`、`IdWorkerUtil`：保留原纪元及节点/时间/序列位布局，
  修复时钟不推进、重复构造生成器和中断丢失。
- `TransactionUtils`、`DoTransactionCompletion`：保留 `transation` 历史包名，
  无事务立即执行，有事务在提交完成后同步执行，回滚不执行。
- 随机与轮询选择器：轮询键状态实例隔离且有界，首次访问不再错误返回 null。
- 责任链、编码枚举查找、JSON、模板替换、分片、缓存回源、MDC、参数校验和网络地址工具。
- `CountDownLatch2`、`ServiceThread`：代际等待、唤醒、停机及实际退出后重启。
- `MethodExpressionEvaluator`：原方法参数和 Bean 引用语义不变，并发发布不突破缓存容量。
- 可选 Git/版本/ping 端点、实际端口与 IPv6 启动 URL 输出。
- 实例字典的 FMM/BMM/BIMM、全组合搜索及评分，HanLP 分词、提取、推荐、繁简转换和词库管理。
- Nacos SDK 客户端及生命周期完整的配置监听。
- 操作日志注解、处理器/操作者覆盖、有界正文捕获和敏感字段过滤。

## 配置和依赖

```yaml
common-tool:
  enabled: true
  application-info-enabled: true
  id-enabled: true
  worker-id: 7
```

多实例部署必须为每个 ID 生成器分配不同的 `worker-id`，范围 0 到 1023。
不配置时自动推导，但这不提供分布式节点唯一性保证，也不保证进程重启后的时间冲突隔离。
检测到时钟回拨立即报错，单毫秒序列耗尽最多等待一秒，已中断线程不会继续取号。

基础装配不强制引入 Web、数据库、Nacos 或 NLP 引擎。
使用事务工具时由消费方提供 `spring-tx`；使用 JSON 工具时提供 Jackson 3；
使用请求地址工具时提供 Servlet API。参数校验使用当前应用已有的 Validator，
可通过 Boot 的 `spring-boot-starter-validation` 提供实现。

## 明确的兼容变化

- 四类请求异常不覆盖 `fillInStackTrace`，改用异常构造参数关闭堆栈写入。
  原因链和 suppressed 异常保留，四类异常仍互不继承。
- `ValidationUtil` 改为构造器接收 Validator 的实例工具，Spring 会在存在 Validator 时自动装配。
  原静态 Spring Bean 抓取不再使用，调用方改为注入工具，避免跨应用上下文污染。
- 轮询的 `clear()` 清理当前实例，不再清理静态全局状态；默认最多保留 10000 个业务键，
  淘汰后再次访问会从初始位置开始。保留原“先递增后选择”的顺序。
- 责任链 Builder 发布后不可继续追加，同一个节点不可重复加入或加入其他 Builder，
  避免悄然修改已发布的链和形成环。
- `Utils.format` 只展开原模板，不再次展开参数值中的占位符，避免 Map 顺序影响结果。
- `Utils.consumerParallel` 默认等待上限为 60 秒，另提供显式超时重载；
  提交被拒绝和消费失败都会传播。任务提交中的调用方执行策略不受该等待上限约束，
  取消 Future 也不保证强制终止已经运行的消费动作。
- MDC 包装任务结束后恢复执行线程原上下文，而不是无条件清空。
- JSON 失败不再返回空字符串或 null，而是抛出 Jackson 异常；合法 JSON null 仍返回 null。
- `NetworkUtil.getClientIp(request)` 默认返回实际对端地址，
  显式确认可信代理后调用 `getClientIp(request, true)` 才恢复代理头提取。
- 事务回调两个重载的无事务行为统一为立即执行，旧文档中的“异步”并非原代码实际行为。

## 元数据

设置 `common-tool.metadata.enabled=true` 后提供 `/git-info`、`/get-version` 和 `/ping`。
无 git.properties 时返回空对象，无 version.json 时返回 404。单个资源默认最多读取一 MiB，
可通过 `common-tool.metadata.max-resource-bytes` 调整，最大十六 MiB。

Git 默认只展示分支、提交和版本字段，不展示仓库地址、构建用户等信息。
受访问控制的部署可显式设置 `common-tool.metadata.include-git-details=true` 恢复完整字段。
启动日志恢复原访问入口输出，优先使用实际随机端口、支持 IPv6 和 HTTPS；
输出入口路径不意味着相应端点已公开，不再打印 Nacos 地址或其他连接配置。

## NLP

`DictionarySegmenter` 改为实例词库，自动配置默认创建空词典，可通过 Bean 覆盖。
正向、逆向和双向匹配读取同一不可变快照，支持超过四字的词和补充平面 Unicode 字符。
全组合使用迭代搜索，默认最多 10000 个组合、1000000 个输出词元；超限明确失败，不返回截断结果。
预算分别由 `common-tool.nlp.max-combinations` 和 `max-output-tokens` 配置。

使用 HanLP 时显式引入 `com.hankcs:hanlp` 并设置 `common-tool.nlp.hanlp-enabled=true`。
当前验证版本为 portable-1.8.4，内置词典能够完成本模块提供的算法，无需运行时下载模型。
`HanlpUtil` 改为实例方法，自定义词典只影响当前实例；同名添加保留原词性和频率，
词语列表仍可由调用方修改，不影响内部词库。
保留原单文本 `suggest`，另提供候选集合重载，使相似句推荐可处理实际候选语料。

## Nacos

显式引入 Nacos SDK 并设置 `common-tool.nacos.enabled=true` 后才连接服务。
`server-addr`、`namespace`、`username`、`password` 支持常用旧
`spring.cloud.nacos.config.*` 配置回退；其他 SDK 参数使用 `common-tool.nacos.client-properties` 透传。
新配置优先，SDK 全属性日志输出强制关闭。凭据不得提交或写入日志。

新开关缺省时兼容旧 `spring.cloud.nacos.config.enabled=true` 的显式启用，
但不沿用旧版“仅引入依赖就默认连接”的行为。配置类不再依赖 Boot 3 专用的 NacosConfigManager。

监听对象继续继承 `AbstractNacosConfig`，提供自己的 ConfigService、dataId、group 和 onReceived。
管理器使用实际 dataId，不再把 Bean 名当配置 ID；初始旧值不会覆盖早到的新值。
注册失败回滚所有已注册监听，关闭时移除回调，外部客户端不由监听器关闭。
默认客户端由 Spring 调用 shutDown。回调队列有界，同一对象串行处理最新状态；
快速变化可能合并中间状态，配置监听不是逐事件审计通道。

真实验证为 SDK 3.1.0、隔离服务端 3.2.3。SDK 明确拒绝发布空白内容，
示例返回 400 并验证原配置不变，不将空文本替换成其他内容。读取/接收空文本的能力仍保留并有单元测试。

```bash
python3 scripts/test-starter.py common --local-nacos
```

该命令创建独占容器、随机 dataId/group、临时客户端目录并在结束后清理，
不会连接现有业务 Nacos。可通过 `TEST_NACOS_IMAGE` 指定已经准备好的兼容服务端镜像。

## 操作日志

显式引入 Boot 4 的 `spring-boot-starter-aspectj`。使用 `@EnableOperationLog` 或配置
`common-tool.operation-log.enabled=true` 激活，显式 false 可关闭注解激活。
保留 `OperationLog` 的类型、描述及请求/响应采集开关，以及全部原日志模型字段。

用户 `OperationLogHandler` 和 `OperatorInfoProvider` Bean 优先。
注解 handler/provider 类型仍支持无参创建，构造失败明确报错；
需要构造器依赖的自定义实现应作为普通 Bean 注册。默认 convertLog 返回模型，不再返回空值。

捕获 JSON 默认最多 4096 字符、16 层，超限或不可序列化返回 `{"_capture":"omitted"}`。
内置密码、密钥、令牌等字段过滤不可关闭，可通过 sensitive-fields 增加业务敏感字段。
规则不能识别所有自由文本中的隐含秘密，业务应将这类字段整体加入过滤集合。
未保留参数名的字符串参数默认隐藏；不读取 Servlet 输入流或展开基础设施对象。

内置处理器默认只输出操作元数据；`log-payloads=true` 才输出已过滤正文。
错误信息默认记录异常类型，不复制可能含凭据的原异常消息；原业务异常仍原样传播。
日志故障不替换业务结果，已有业务异常会保留日志故障为 suppressed。
`trust-proxy-headers` 默认 false；仅在入口代理已删除并重建外部转发头时启用。

## 示例与验证

`examples-starter/common-tool-example` 真实依赖本 Starter，提供 ID、校验、模板、
分片、MDC、并行消费和实际 JDBC 提交/回滚接口。
示例自行创建随机命名的内存 H2 数据库，不使用业务数据源；事务记录使用随机 ID 并在请求结束后删除。

```bash
python3 scripts/test-starter.py common
```

自动化会执行全量构建、启动示例、检查业务响应并清理进程。
表达式工具变更后还需实际回归 lock、rate-limiter、idempotent。
逐项实际检查结果和最终验收状态以 MIGRATION.md 为准。
