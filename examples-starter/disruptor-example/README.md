# Disruptor 示例

真实依赖 Starter，通过 `MessageHandler<Long>` Bean 注册累加消费者。
`POST /api/disruptor/send?value=2` 等待消费确认后返回累计值；负数模拟业务异常并返回 409。

运行 `python3 scripts/test-starter.py disruptor` 自动完成构建、随机端口启动、并发请求和退出。
手动运行 `java -jar examples-starter/disruptor-example/target/disruptor-example-4.0.0-SNAPSHOT.jar`。
