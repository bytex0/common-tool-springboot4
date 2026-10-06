# 基础工具 Starter

坐标：`io.github.bytex0:common-tool-spring-boot4-starter`。
基线为 Java 21、Spring Boot 4，JSON 工具使用 Jackson 3。
当前仍在补齐原版功能，不代表完整迁移，逐项状态见 [MIGRATION.md](MIGRATION.md)。

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

## 示例与验证

`examples-starter/common-tool-example` 真实依赖本 Starter，提供 ID、校验、模板、
分片、MDC、并行消费和实际 JDBC 提交/回滚接口。
示例自行创建随机命名的内存 H2 数据库，不使用业务数据源；事务记录使用随机 ID 并在请求结束后删除。

```bash
python3 scripts/test-starter.py common
```

自动化会执行全量构建、启动示例、检查业务响应并清理进程。
表达式工具变更后还需实际回归 lock、rate-limiter、idempotent。
操作日志、Nacos、NLP、元数据接口和完整兼容性验收仍在迁移清单中，不能据此标记本 Starter 完成。
