# Excel 示例

真实依赖 `excel-spring-boot4-starter`，默认 `127.0.0.1:18084`。
示例自建随机名称的 H2 内存数据库和临时目录，不连接业务数据源，无需外部服务。

- `GET /api/excel/export?count=11&rowsPerSheet=5`：返回跨3个Sheet的XLSX。
- 增加 `zip=true`：返回多个XLSX组成的ZIP。
- `POST /api/excel/import?sheet=0`：multipart字段`file`，返回行编号、总行数及失败数。
- 行数参数非法、非Excel文件返回400。示例单文件上传上限16MB。
- `GET /api/excel/legacy/export?mode=multi&count=11&pageSize=4&rowsPerSheet=5`：
  通过原处理器导出，mode 可选 simple/multi/zip。
- `POST /api/excel/legacy/import?mode=large&sheet=0&batchSize=2`：
  multipart 字段 `file`；mode 可选 simple/large，返回数据库实际行内容、计数及进度。
- 并发导入增加 `continueOnError=true&failAt=3` 可模拟第二批失败；
  `transactional=true` 回滚该批，false 保留失败前的实际写入，统计仍按批次行数。
- `GET /api/excel/legacy/state`：核对测试表残留行数及临时工作簿数量，操作结束后都应为 0。

导入由服务器生成每次唯一的 run_id，任何成功或失败都只删除本次数据。
临时目录仅由当前示例创建和销毁，HTTP 不接受任意本地目录参数。

执行 `python3 scripts/test-starter.py excel`，自动构建、启动和健康检查，下载实际文件，
逐 Sheet 回读编号顺序与中文名称，再核对 ZIP、空文件、事务与非事务结果、错误响应和清理状态。
仅在当前源码已经构建时使用 `--skip-build`。报告写入 `target/api-test-report.json`，最后停止示例进程。

普通测试还验证 XLS 格式、输入流所有权、ZIP 生成中失败清理、进度一致性、
实际线程取消、用户执行器覆盖及 H2 批次回滚。
