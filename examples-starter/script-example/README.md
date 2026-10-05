# 脚本示例

`GET /api/script/run?name=sum&a=2&b=3` 返回 5，name 仅接受 sum、product、timeout。
示例真实依赖并自动装配脚本 Starter，不接收任意代码。

运行 `python3 scripts/test-starter.py script` 自动构建、启动和验证后清理进程。
手动运行 `java -jar examples-starter/script-example/target/script-example-4.0.0-SNAPSHOT.jar`。
