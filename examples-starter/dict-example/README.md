# 字典示例

真实依赖字典Starter，默认本机18087端口，无需外部服务。

- GET `/api/dict/sample?status=1`，返回重命名后的state/stateText和numeric/numericText。
- PUT `/api/dict/status`，JSON code到text映射，替换测试字典并刷新缓存。
- POST `/api/dict/refresh`，整体刷新。
- PUT `/api/dict/source`，只修改来源，验证显式刷新前后数据不同。
- GET `/api/dict/department?code=D1`，通过 `table/field/codeField` 查询实际 H2 表。
- GET `/api/dict/legacy?value=Engineering`，执行原四参数同列匹配查询。

示例 POM 实际依赖 `dict-spring-boot4-starter`。Boot 管理独立的 H2 内存数据库与 Hikari 连接池，
`schema.sql/data.sql` 只初始化本次进程的测试表，关闭应用后清理资源，不需要真实数据库凭据。

执行 `python3 scripts/test-starter.py dict` 自动构建、启动并验证编码保留、文本补充、数值类型、
来源与缓存隔离、全量刷新、未知编码、数据库回退、绑定参数和非法数据更新。
只有已完成全量构建时才添加 `--skip-build`，报告位于 `target/api-test-report.json`。
