# Disruptor 示例

真实依赖 Starter，通过 `MessageHandler<Long>` Bean 注册累加消费者。
`POST /api/disruptor/send?value=2` 等待消费确认后返回累计值；负数模拟业务异常并返回 409。

运行 `python3 scripts/test-starter.py disruptor` 自动完成构建、随机端口启动、并发请求和退出。
手动运行 `java -jar examples-starter/disruptor-example/target/disruptor-example-4.0.0-SNAPSHOT.jar`。

示例真实启用 Micrometer，监听注解包含两个平台工作线程和一个独立虚拟线程队列。
其他接口：

- `POST /api/disruptor/listener?queue=annotated&value=1`：等待真实注解方法消费，负值返回 409。
- `GET /api/disruptor/listeners`：处理次数、最大并发和观察到的线程模式。
- `POST /api/disruptor/queues/{name}`：原动态工厂，参数 size、producer、wait、virtual、delay。
- `POST /api/disruptor/queues/{name}/raw`：原原生 Disruptor 注册及手工指标入口。
- `POST /api/disruptor/queues/{name}/send?value=1&legacy=true`：原 void 发送，只确认入队；
  legacy=false 使用消费确认，原生外部队列不支持该模式。
- `GET/DELETE /api/disruptor/queues/{name}`：统计或删除队列。
- `GET /api/disruptor/metrics?name=...`：独立检查指标是否随队列删除。

动态名称限制为 `dynamic-*`，环容量 2 至 1024，每条测试消息延时最多一秒。
脚本验证全部七种等待策略、SINGLE 多发布线程、一次消费、两种线程模式、满队列 429、
同名重建和指标清理，最后停止自己的示例进程。无需外部服务。
已完成全量构建时可以加 `--skip-build`；报告位于 `target/api-test-report.json`。
