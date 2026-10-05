# Disruptor

依赖 `io.github.bytex0:disruptor-spring-boot4-starter`，声明 `MessageHandler<T>` Bean，
实现 `name()`、`type()` 和 `handle(message)`；注入 `DisruptorTemplate`，调用 `send(name, message)`。
返回的 `CompletableFuture<Void>` 表示实际消费成功或失败，不只表示入队。队列满立即抛出拒绝异常。
配置 `disruptor.enabled=false` 关闭；`buffer-size` 默认 1024，必须是 2 至 1048576 的二次幂。

审查并修复：旧名称覆盖泄漏工作线程、停机无限等待、缓冲区保留对象、吞消费异常、
监听器线程参数未使用、处理器初始化过早。采用 SmartLifecycle 在 Bean 就绪后启动；
重复名称启动失败，无消费者时不启动线程。消息按队列顺序消费，多线程生产。
停机每队列最多等待消费 5 秒，随后中断工作线程并等待 1 秒，未完成确认全部失败。
业务处理器必须响应中断；JVM 无法安全强杀忽略中断的业务代码。

兼容变化：使用类型化消费者 Bean 替代旧反射监听注解和动态队列注册；固定 MULTI/BLOCKING/
单平台线程顺序消费，移除旧无效线程数选项、Prometheus 专属绑定与原生 Disruptor 外露。
该组件不是持久消息队列，不提供重试或跨进程投递保证。

验证 `python3 scripts/test-starter.py disruptor`：实际消费、失败后恢复、多生产者、非法队列；
Java 测试还覆盖满队列拒绝、重复名称、关闭开关、覆盖 Bean 和关闭后发送。
