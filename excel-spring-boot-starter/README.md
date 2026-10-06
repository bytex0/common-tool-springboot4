# Excel Starter

基于 Apache Fesod 2，提供现有 `ExcelTemplate` 和完整的原上下文/抽象处理器/静态工具入口。
默认启用，`excel.enabled=false` 关闭模板和默认执行器，用户 Bean 可替换。
逐项功能、行为修正及验收证据见 [MIGRATION.md](MIGRATION.md)。

```xml
<dependency>
    <groupId>io.github.bytex0</groupId>
    <artifactId>excel-spring-boot4-starter</artifactId>
</dependency>
```

## 流式模板

- `write(output, entityClass, sheetName, supplier, rowsPerSheet)`：保留现有带序号命名，按行拆分 Sheet。
- `writeZip(output, entityClass, supplier, rowsPerFile)`：每次只生成一个临时工作簿，加入 ZIP 后复用临时文件，结束及失败均清理。
- `writeNamed` / `writeZipNamed`：支持兼容处理器的一基名称及自定义临时根目录，不把外部标识拼接为临时路径。
- `read(input, entityClass, sheetNo, batchSize, consumer, continueOnError)`：按文件签名识别 XLS/XLSX，
  不把任意文本猜测成空 CSV；返回成功/失败批次的行数。
- `readWithListener`：保留成品引擎监听器接入，使用 Apache Fesod `ReadListener`，不复制解析引擎。
- Supplier 每次返回下一批数据，空列表表示 EOF；禁止返回 null，每批最多 10000 行。可在 Supplier 内使用游标查询，不强制深度分页。
- 每 Sheet/文件最多 1000000 数据行，预留表头空间。空数据也生成有效的表头工作簿。列宽自适应，启用 SXSSF 临时文件压缩。
- 模板不关闭调用方输入输出流。显式输入保护层避免 XLS 底层解析器无视 `autoCloseStream=false` 时提前关闭流。
- 低层 `read` 的 consumer 事务仍由调用方决定；需要原并发导入的事务开关时使用下述 `LargeDataExcelImporter`。
- 流式输出无法回滚已发送的字节，需要“全部成功再返回”时应用可先写自己的临时文件。

## 原处理器

保留原包层级和类名，统一根包为 `io.github.bytex0.excel`：

| 入口 | 能力 |
| --- | --- |
| `AbstractSimpleExcelProcessor<R,P>` | `handleImportData`、`getExportData`、`importExcel`、`exportExcel` |
| `MultiSheetExcelExporter<R,P>` | `getExportData`、`getTotalCount`、`exportMultiSheetExcel` |
| `LargeExcelZipExporter<R,P>` | `getExportData`、`getTotalCount`、`exportLargeExcelToZip` |
| `LargeDataExcelImporter<R>` | `handleImportData`、`importLargeExcel`，新增 `importLargeExcelWithResult` 返回统计 |
| `ExcelUtil` | 保留静态 `exportToExcel` / `importFromExcel`，没有静态可变状态 |

原 `ExportContext`、`MultiSheetExportContext`、`LargeExcelZipExportContext`、
`ImportContext`、`LargeDataImportContext` 全部恢复。保留字段、重载、默认值和浅 `clone`；
分页对局部副本推进，不改变调用方的 currentPage/pageSize。
查询参数引用仍是浅复制，操作期间不得修改共享上下文或查询参数。

普通导出现在真正写入 HTTP 响应，不再误写本地文件。普通导入尊重 `sheetNo`，null 表示 0。
处理器设置标准 XLSX/ZIP MIME 和 UTF-8 Content-Disposition，文件名去除路径和控制字符。
处理器关闭自己打开的上传流，不关闭容器响应流和外部执行器。

## 分页导出

- 多 Sheet 和 ZIP 保留原总数及分页查询回调，页码从 1 开始。
- ZIP 使用 `fetchSize` 覆盖单页副本的 pageSize，保留 maxRowsPerSheet 的每文件行数含义。
- 查询和写出流水执行：写当前批次时预取下一页，最多一个在途查询和有限批次缓存。
  不再一次创建全部页码表、全部文件任务或依赖无界虚拟线程。
- 页序确定，Sheet/文件边界在批次内部拆分，页大小不整除上限也不会多行、丢行或重复。
- 总数与每页必须来自稳定排序、同一查询条件的快照；数量不一致明确失败，不静默导出缺行文件。
  请求线程的事务不会自动跨到异步查询线程。
- 多 Sheet 保留 `name_1`、`name_2` 命名；ZIP 文件为 `name_1.xlsx` 等。
  修正旧 ZIP 第一张 Sheet 错从第二个序号开始的问题。长名称为序号预留空间并避免切断代理对。
- 空数据返回合法表头工作簿，ZIP 包含一个表头工作簿，而不是空响应。
- `tempDir` 为空使用系统临时目录；不为空可作为临时根目录，根目录本身不由导出清理。
  每次使用随机文件，`uniqueId` 仅保留为业务标识。成功、读取失败和写出失败均清理本次文件；
  清理异常附加于原始异常，不覆盖最初失败。
- `operationTimeout` 默认 5 分钟，控制分页等待的协作式预算；
  `cancellationTimeout` 默认 10 秒，取消后等待业务查询真正退出。查询必须响应中断并配置自己的 I/O 超时。

## 并发导入

原 `importLargeExcel` 会等待本次操作完成，保留这一实际调用语义；内部在执行器并发处理批次。
不提供提交后立即返回后台任务 ID 的协议。

| 上下文字段 | 默认值与规则 |
| --- | --- |
| batchSize | 1000，范围 1 至 10000，覆盖普通导入的 batchCount |
| queueSize | 10，范围 1 至 1024，表示允许排队的批次数 |
| threadCount | 4，范围 1 至 128，实际并发还受共享执行器限制 |
| enableTransaction | true，每批独立事务，需使用带 PlatformTransactionManager 的构造器 |
| continueOnError | false，业务批次异常停止；true 时累计失败批次并继续 |
| operationTimeout | 5 分钟，在读取检查点、排队和处理等待处检查 |
| cancellationTimeout | 10 秒，停止时在共同预算内等待任务真正退出 |

旧 `enableTransaction` 没有实现。现在通过 `TransactionTemplate` 为每批创建
`REQUIRES_NEW` 事务，回调或提交失败时按管理器语义回滚该批。
保留单执行器构造器，但此用法须明确 `enableTransaction=false`；
默认开启却没提供事务管理器会在打开文件前失败，不假装有事务。

继续模式中，成功/失败按**原始批次行数**计数，不因回调清空自己的列表而失真。
未开启事务时，一个失败批次可能已有部分业务写入，failed 不是数据库实际回滚行数。
已成功提交的前序批次不会因为后续失败而全文件回滚。
解析、调度、中断和进度回调错误始终停止，不受 continueOnError 影响。

`ImportProgressCallback` 保留四个参数，由调用线程串行通知，不持锁调用用户代码：
current 始终等于 success+failed，读取过程中 total=-1，最终通知含实际总行数。
不同导入操作之间不共享计数。进度是累计快照，最后的批次通知和完成通知可能相同。

在途任务上限为 threadCount+queueSize。失败时不会继续无限等待满队列；
使用可中断的真实任务，并区分 Future 已取消与业务方法真正退出。
业务不响应中断时会在清理上限后明确失败，不能强制终止 Java 用户代码。
同步输入 I/O、进度回调和业务数据库仍需自身超时，协作式预算不承诺强行中断它们。

## 执行器与依赖

```yaml
excel:
  enabled: true
  thread-pool:
    core-size: 4
    max-size: 4
    queue-capacity: 128
    keep-alive-seconds: 60
    shutdown-timeout: 10s
```

保留 `excelThreadPool` Bean 名和原配置工厂入口，参数现在实际生效。
默认使用有界平台线程池和明确拒绝策略，关闭有等待上限，不建立静态全局池。
用户同名 `ExecutorService` Bean 优先，外部执行器由其所有者关闭。
不能在该池的唯一工作线程里调用会提交并等待同池任务的阻塞导出/导入流程。

模型注解和 `PageReadListener` 等 SDK 类型从 `cn.idev.excel` 迁到 `org.apache.fesod.sheet`。
普通模板可以在没有 Servlet 的类路径运行；旧 HTTP/Multipart 处理器需要可选的 Spring Web/Servlet API。
Spring TX 只提供事务抽象，不自动创建数据库或要求消费方连接中间件。

验证：`python3 scripts/test-starter.py excel`。示例使用自己创建的 H2 和临时目录，
核对真实文件内容、回滚、进度及失败清理，运行方式见 [示例](../examples-starter/excel-example/README.md)。
