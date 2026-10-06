# 基础能力示例

真实依赖 `common-tool-spring-boot4-starter`，ID 和校验工具来自自动配置。
数据库由示例直接创建为随机命名的内存 H2，不读取业务数据源配置。

| 接口 | 验证范围 |
| --- | --- |
| `GET /api/demo/ping` | 原响应协议与 Starter 装配 |
| `GET /api/common/ids?count=100` | 字符串 ID、数量边界、并发唯一性 |
| `POST /api/common/validate` | JSON 模型校验，正文如 `{"name":"test","age":1}` |
| `POST /api/common/transaction?rollback=false` | 实际 JDBC 提交与两个提交回调 |
| `POST /api/common/transaction?rollback=true` | 实际回滚后无数据、无提交回调 |
| `GET /api/common/trace` | 自定义分隔符与直接执行后的 MDC 恢复 |
| `GET /api/common/tools` | 自动配置轮询、索引分片、模板替换 |
| `POST /api/common/parallel?fail=false` | 有界线程池并行消费 |
| `POST /api/common/parallel?fail=true` | 消费异常传播及后续任务恢复 |

```bash
python3 scripts/test-starter.py common
```

只有已经完成当前代码构建时才使用 `--skip-build`。
测试报告为 `target/api-test-report.json`，不会提交到 Git。
本示例没有验证尚未迁移的操作日志、Nacos、NLP 和元数据接口。
