# Excel 示例

真实集成 Excel Starter，默认 `127.0.0.1:18084`，无需外部服务。

- `GET /api/excel/export?count=11&rowsPerSheet=5`：返回跨3个Sheet的XLSX。
- 增加 `zip=true`：返回多个XLSX组成的ZIP。
- `POST /api/excel/import?sheet=0`：multipart字段`file`，返回行编号、总行数及失败数。
- 行数参数非法、非Excel文件返回400。示例单文件上传上限16MB。

执行 `python3 scripts/test-starter.py excel`，自动构建和启动，下载实际文件后通过导入接口逐Sheet回读，比对顺序和行数，再验证ZIP、空文件、非法参数及进程退出。普通构建中的测试还覆盖失败传播和批次继续模式。
