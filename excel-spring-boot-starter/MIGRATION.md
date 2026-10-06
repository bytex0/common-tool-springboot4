# Excel 功能对照

参考同级 `common-tool/excel-spring-boot-starter` 全部 13 个源码文件、POM 与示例文档。
2026-10-06 本轮补齐已通过分项验收。下表列出原入口、适配实现与实际验证，
不再以先前同步模板的局部测试替代功能完整性。

| 原文件/符号 | 迁移位置和预期行为 | 状态 |
| --- | --- | --- |
| ExcelProperties、ExcelThreadPoolConfig.excelThreadPool | 同名配置及命名执行器，原文档参数实际生效；有界容量和有限关闭等待 | ExcelConfigurationTest 绑定、关闭、覆盖及无 Web 类路径通过 |
| ExportContext、MultiSheetExportContext、LargeExcelZipExportContext | 同名模型保留全部原属性、默认值及浅 clone，页查询只更新局部副本 | ExcelCompatibilityTest 页序及原上下文不变；ZIP fetchSize 适配通过 |
| ImportContext、LargeDataImportContext、ImportProgressCallback | 同名模型保留文件/Sheet/批次/并发/队列/事务/继续模式/回调 | LargeImportTest、事务测试与真实上传接口通过 |
| AbstractSimpleExcelProcessor.importExcel/exportExcel/handleImportData/getExportData | 同名方法和原业务回调，输出响应流并读取选中的 Sheet | ExcelCompatibilityTest、LegacyExcelControllerTest、simple HTTP 往返通过 |
| MultiSheetExcelExporter.getExportData/getTotalCount/exportMultiSheetExcel | 同名入口，经 OrderedPageSource 有界预取，ExcelTemplate 精确拆分 | 单元及 multi HTTP 页大小 4/Sheet 上限 5 的完整行序和中文内容通过 |
| LargeExcelZipExporter.getExportData/getTotalCount/exportLargeExcelToZip | 同名入口，保留临时根目录及文件名；单临时文件复用，所有退出路径清理 | ExcelResourceTest 生成中失败清理、ZIP 内容/名称/上限与实际 state 检查通过 |
| LargeDataExcelImporter.handleImportData/importLargeExcel | 原方法等待完成，内部有界并发；增加返回统计的 importLargeExcelWithResult | LargeImportTest 失败停止、实际任务中断退出和一致进度通过 |
| enableTransaction / continueOnError | TransactionTemplate 按批 REQUIRES_NEW，继续模式累计原批次大小 | ExcelTransactionTest 与 H2 实际 HTTP：开启回滚行 3，关闭保留行 3，计数与内容均通过 |
| ExcelUtil.exportToExcel/importFromExcel | 同名静态无状态方法；PageReadListener 类型迁为 Apache Fesod | ExcelResourceTest 静态往返及上传流关闭通过 |
| 当前 ExcelTemplate.write/writeZip/read | 保留旧模板入口，新增 writeNamed/writeZipNamed/readWithListener；输入输出关闭隔离 | 原模板全部回归，XLS 文件签名与流所有权、空工作簿/ZIP、模板 HTTP 全部通过 |

## 已识别问题

- 普通处理器导出设置了响应头，但实际把数据写入本地路径，没有写 HTTP 响应。
- 并发导入的工作线程失败后，读取方仍可能永久等待满队列；取消 CompletableFuture 不能确保工作线程中断。
- 导入成功/失败计数非一致快照，失败批次不计入 current，enableTransaction 原来没有生效。
- 多 Sheet 异步队列到达顺序决定行顺序，生产或写出一端失败可能让另一端永久等待。
- 页大小与 Sheet/文件上限不整除时，整页分配会超行；空数据返回不是有效工作簿。
- ZIP 目录使用外部 uniqueId 拼路径、默认 tempDir=null、生成中失败不完整清理。
- 原公共配置没有实际容量字段，线程池是无界 per-task 执行器。

迁移保留原成品入口，不要求消费方自行重写异步流程或事务处理；明确记录修正后的边界和测试。

## 兼容与优化

- SDK 类型从 `cn.idev.excel` 迁到 `org.apache.fesod.sheet`，模型注解和监听器需要调整 import。
  原 Java 包层级、方法和上下文保留，普通回调的原始 ImportContext 签名没有擅自改写。
- 不再依赖队列到达顺序写 Sheet，也不一次分配所有文件和页码任务。
  写当前页时预取下一页，保留异步查询能力，以有限内存和确定顺序替代无界文件并发。
- 分页总数和数据需来自稳定排序/一致条件，返回数量不一致会失败，不静默生成缺行文件。
- ZIP Sheet 序号修正为从 1 开始；tempDir 空值使用系统目录，uniqueId 不再用于路径拼接。
- 原导入/导出方法均阻塞等待完成，原 README 的“后台避免请求超时”不代表已有任务 ID 异步协议。
- 原事务开关曾未实现。现在默认 true 必须提供事务管理器，单执行器构造入口可显式关闭事务。
  事务只覆盖各批，不回滚此前已提交的批次；无事务模式的失败计数不能解释为物理回滚行数。
- 进度改在调用线程串行通知，current=success+failed，最后包含准确 total。
  业务可修改自己的批次容器，不影响统计和其他读取批次。
- 新的超时为协作式预算，无法安全强制终止任意 Java 业务代码。
  读取 I/O、进度回调和数据库须设置自身超时，业务须响应中断；
  取消等待超时时明确抛错，不把 Future 已取消当作业务已停止。
- 模板用显式保护层避免 XLS 解析器忽略 autoCloseStream 后关闭调用方流；
  旧上传工具/处理器只关闭自己打开的输入流，输出由容器或调用方管理。

## 最终验收

2026-10-06：

- 功能对齐：上表原成品入口已恢复，新增操作只用于修复原失效参数和安全边界。
- 编码规范：Starter、示例和测试接入 Checkstyle；完整注释与禁止用法扫描通过。
  人工核对流所有权、取消竞争、异常原因、SQL 参数绑定、临时路径和配置隔离，
  不宣称所有阿里规则或全仓功能已自动验收。
- 全量构建：`mvn --batch-mode --no-transfer-progress clean verify` 通过，
  311 项 Java 测试，失败、错误、跳过均为 0；本 Starter 17 项，示例 MVC 6 项。
- 真实联调：`python3 scripts/test-starter.py excel --skip-build` 的 14 项检查通过。
  验证原模板、三个旧处理器的文件及中文内容、事务/非事务真实结果、进度、错误、空数据和清理状态。
- 工程检查：9 项 Python 测试及 18 个自有坐标、BOM/示例依赖、普通库 JAR 校验通过，
  `git diff --check` 通过。

真实测试使用独立 H2、随机 run_id 和本示例临时目录，没有访问业务数据库。
测试行、临时工作簿及示例进程均已清理，报告保留在示例 target 下，不提交构建产物。
