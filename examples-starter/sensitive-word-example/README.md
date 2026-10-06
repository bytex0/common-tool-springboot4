# 敏感词示例

真实依赖并自动装配敏感词 Starter。提供 `POST /api/sensitive/process` 和 `/api/sensitive/reject`，
请求体为 `text/plain; charset=utf-8`。示例词为 bad、badly，白名单为 badge。

执行 `python3 scripts/test-starter.py sensitive-word` 自动构建、随机端口启动、验证内容并清理进程。
手动运行构建产物：`java -jar examples-starter/sensitive-word-example/target/sensitive-word-example-4.0.0-SNAPSHOT.jar`。

新增接口全部通过 Starter 自动配置，不复制过滤算法：

- `GET /api/sensitive/legacy?text=...&mode=MIN_MATCH`：原闭区间、分类、字符替换和高亮。
- `PUT /api/sensitive/words?category=...`：JSON 词条数组批量添加；DELETE 同路径删除指定 word。
- `PUT/DELETE /api/sensitive/whitelist`：白名单增加/删除，立即影响全部业务处理入口。
- `POST /api/sensitive/load-example`：只加载固定内置资源，不接受任意文件路径。
- `DELETE /api/sensitive/words/all`：测试实例内清空词库。
- `POST /api/sensitive/annotations/parameter`：独立参数注解，text 被处理，other 不受影响。
- `POST /api/sensitive/annotations/document`：JSON content 字段最长匹配替换，other 保留原文。
- `POST /api/sensitive/annotations/reject`、`/annotations/modes`：方法拒绝、高亮及仅检测。
- `POST /api/sensitive/web/json`、`/web/text`：真实 Web 过滤，分别只接收 JSON 和 text/plain。
- `GET /api/sensitive/web/query`：只过滤 selected 参数；`POST /web/excluded` 不过滤。

Web 默认仅保护 `/api/sensitive/web/**`，上限 4096 字节，使用 REPLACE。
自动化另起独立进程验证 EXCEPTION/HIGHLIGHT/DETECT_ONLY，所有进程均自动清理。
已完成全量构建时可加 `--skip-build`。报告在 `target/api-test-report.json`。
示例只绑定 127.0.0.1，管理接口未实现业务鉴权，不应暴露到公网。
