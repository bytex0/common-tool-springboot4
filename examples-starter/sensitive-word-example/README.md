# 敏感词示例

真实依赖并自动装配敏感词 Starter。提供 `POST /api/sensitive/process` 和 `/api/sensitive/reject`，
请求体为 `text/plain; charset=utf-8`。示例词为 bad、badly，白名单为 badge。

执行 `python3 scripts/test-starter.py sensitive-word` 自动构建、随机端口启动、验证内容并清理进程。
手动运行构建产物：`java -jar examples-starter/sensitive-word-example/target/sensitive-word-example-4.0.0-SNAPSHOT.jar`。
