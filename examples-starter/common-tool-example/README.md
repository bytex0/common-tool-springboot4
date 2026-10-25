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
| `GET /git-info`、`GET /get-version`、`GET /ping` | 元数据资源、默认字段过滤及原 ping 协议 |
| `GET /api/common/nlp/dictionary` | 双向分词、长词及完整组合数量 |
| `GET /api/common/nlp/hanlp` | 实际 HanLP 分词、词性、索引、命名实体、关键词、摘要、短语、推荐和繁简转换 |
| `POST /api/common/nlp/custom` | 随机自定义词的新增、分词和删除 |
| `POST /api/common/audit?mode=success` | 请求/响应过滤及真实 Web 元数据 |
| `POST /api/common/audit?mode=failure` | 业务失败日志，原业务状态不被覆盖 |
| `POST /api/common/audit?mode=no-payload` | 请求/响应采集关闭 |
| `POST /api/common/audit?mode=large` | 大响应捕获限制 |
| `POST /api/common/audit?mode=handler-failure` | 日志处理器故障不改变业务结果 |
| `GET/POST/DELETE /api/common/nacos/config` | 仅显式联调启用，操作随机测试配置及检查监听状态 |

```bash
python3 scripts/test-starter.py common
```

只有已经完成当前代码构建时才使用 `--skip-build`。
测试报告为 `target/api-test-report.json`，不会提交到 Git。
操作日志请求体示例为 `{"name":"test","password":"fixture-value","token":"fixture-value"}`，
返回值只包含经切面过滤的日志，不回传原始内部敏感结果。

Nacos 真实联调单独显式运行：

```bash
python3 scripts/test-starter.py common --local-nacos
```

脚本启动独占 Nacos 容器，使用随机配置标识和临时缓存目录，屏蔽继承的业务 Nacos 环境配置。
空白发布按 SDK 契约拒绝，同时验证已保存配置和回调内容不变。
普通 Maven 测试不会连接 Nacos。
