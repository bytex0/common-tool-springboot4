# Excel Starter

基于 Apache Fesod 2，提供 `ExcelTemplate`：有界批次、多 Sheet、ZIP 分文件导出及批次导入。默认启用，`excel.enabled=false` 可关闭默认模板，用户 Bean 可替换。

```xml
<dependency>
    <groupId>io.github.bytex0</groupId>
    <artifactId>excel-spring-boot4-starter</artifactId>
</dependency>
```

- `write(output, entityClass, sheetName, supplier, rowsPerSheet)`：按行拆分 Sheet，支持页边界与 Sheet 边界不对齐。
- `writeZip(output, entityClass, supplier, rowsPerFile)`：每次只生成一个临时工作簿，加入 ZIP 后复用临时文件，结束及失败均清理。
- `read(input, entityClass, sheetNo, batchSize, consumer, continueOnError)`：明确读取XLSX，不把任意文本自动猜测成CSV；返回成功/失败行数，默认传播消费异常，继续模式显式累计失败行数。
- Supplier 每次返回下一批数据，空列表表示 EOF；禁止返回 null，每批最多 10000 行。可在 Supplier 内使用游标查询，不强制深度分页。
- 每 Sheet/文件最多 1000000 数据行，预留表头空间。空数据也生成有效的表头工作簿。列宽自适应，启用 SXSSF 临时文件压缩。
- 不关闭调用方输入输出流；不接管数据库事务。HTTP 响应由应用设置正确 MIME 和文件名，示例使用 UTF-8 Content-Disposition。
- 流式输出无法回滚已发送的字节，需要“全部成功再返回”时应用可先写自己的临时文件。

本次有意用一个模板替代旧的多套抽象导出/导入上下文，属于源代码不兼容升级。修复旧普通导出写到磁盘而不是响应、异步队列失败时可能无限等待、页大小与 Sheet 上限不整除时超行、空文件无有效内容及生成失败时临时文件残留。取消未实现事务语义的开关及无界线程池；需要并行查询时由应用明确安排有界任务。

实体注解使用 `org.apache.fesod.sheet.annotation.ExcelProperty`，不再使用 `cn.idev.excel`。核心不依赖 Servlet。验证命令：`python3 scripts/test-starter.py excel`。
