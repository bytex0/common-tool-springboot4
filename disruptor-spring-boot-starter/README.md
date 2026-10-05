# Disruptor

依赖 `io.github.bytex0:disruptor-spring-boot4-starter`，声明 `MessageHandler<T>` Bean，
实现 `name()`、`type()` 和 `handle(message)`；注入 `DisruptorTemplate`，调用 `send(name, message)`。
返回的 `CompletableFuture<Void>` 表示实际消费成功或失败，不只表示入队。队列满立即抛出拒绝异常。
配置 `disruptor.enabled=false` 关闭；`buffer-size` 默认 1024，必须是 2 至 1048576 的二次幂。

## 原入口恢复

- 原 `template.DisruptorTemplate`、`handler.MessageHandler<T>`、`MessageHandlerAdapter`、
  `DisruptorHandler`、`DisruptorEvent/DisruptorEventFactory` 均保留在原相对包路径。
  原事件处理器接口的参数是 `DisruptorEvent<T>`，与根包类型化接口不同。
- 原模板保留可空 `DisruptorMetrics` 构造器、`send/createQueue/registerDisruptor/registerMetrics/shutdown/shutdownAll`。
  `createQueue` 原参数全部生效，返回已经启动的 LMAX 实例，不再覆盖同名队列泄漏旧线程。
- 根包类型化模板 Bean 名为 `disruptorTemplate`；原包模板 Bean 名为 `legacyDisruptorTemplate`，
  两者按类型注入并共享引擎。原显式按 Bean 名使用旧模板的代码需改用 `legacyDisruptorTemplate`。
- 原 `@DisruptorListener` 恢复，方法必须只有一个消息参数，方法返回值忽略。
  Spring 代理调用保留，签名错误或重复队列在启动时明确失败，不吞掉初始化错误。
- 原七种等待策略全部保留：BLOCKING、YIELDING、BUSY_SPIN、SLEEPING、TIMEOUT_BLOCKING、
  LITE_BLOCKING、PHASED_BACKOFF，使用 LMAX 实现。自旋策略会持续使用 CPU，不宜随意增加线程数。

## 配置与线程

```yaml
disruptor:
  enabled: true
  buffer-size: 1024
  producer-type: MULTI
  wait-strategy: BLOCKING
  threads: 1
  virtual-thread: true
  max-queues: 64
  publish-timeout: 5s
  shutdown-timeout: 5s
  enable-metrics: false
```

配置现在实际用于类型化处理器和监听注解。注解 `inheritDefaults=true` 时，保持原默认值的属性
采用全局配置；设为 false 可固定使用注解值，包括与默认值相同的显式值。
原来的 `threads`、`virtualThread` 不再被忽略。多工作线程按序号分配消息，每条消息处理一次，
不是广播重复执行，也不是抢占式工作窃取；仅单工作线程保证队列消费顺序。
原手工 ThreadFactory 参数仍优先，必须快速返回未启动线程。

SINGLE 使用真实 SingleProducerSequencer，但发布入口用显式锁串行化，多个调用线程也能安全使用。
无处理器且无动态队列时不创建消费线程；监听方法在单例初始化完成后注册。
原始环事件会被复用，业务不得在消费结束后持有事件引用或修改其内部确认。

## 确认与停止

- 根包 `send` 和原包新增 `sendAsync` 返回实际消费确认，满队列立即抛出 RejectedExecutionException。
- 原 void `send` 只表示已经入队，满载最多等待 `publish-timeout`，不再无限卡住。
  等待范围 0 至 1 分钟，零表示立即拒绝；消费线程向自己的满队列发送立即拒绝，避免自锁。
- 原生 `registerDisruptor` 保留调用方消费链，调用方必须先配置并启动实例。
  原生外部队列只使用 void `send`，不支持消费确认，不能伪造已经完成业务的 Future。
  引擎不能识别外部任意处理器的线程归属，外部线程工厂与回调仍须配合停止。
- 普通业务异常完成失败确认但不停止队列；原异常原因保留在 Future，不输出消息正文。
  致命 Error 停止该队列并结束待确认结果。
- `shutdown(name)` 删除单个队列并允许同名重建；`shutdownAll()` 清空当前队列后仍可创建；
  `close/stop` 永久关闭模板，关闭后不能重启。
- 每队列优雅停止默认 5 秒，可设置为 1ms 至 1 分钟；之后停止环、中断自己创建的线程，
  总计额外最多等待 1 秒退出，剩余确认失败。消费者自关闭不等待自己。
  注册/发布锁不用于等待线程或调用业务回调。

业务必须响应中断，JVM 无法安全强杀忽略中断的代码。超时或失败确认不能撤销已经发生的副作用；
本组件不是持久消息队列，不提供重试、分布式事务或跨进程投递保证。

## 指标与验证

`enable-metrics=true` 时须提供 MeterRegistry，恢复原
`disruptor.buffer.size`、`disruptor.remaining.capacity` 及 `queue` 标签。
队列关闭移除自己的指标，同名重建指向新环；指标组件关闭也清理手工登记项，不关闭外部注册表。
运行时 `stats(name)` 提供发布、消费、失败计数。外部原生队列的消费和线程不受引擎包装，
`managed=false` 时相应引擎计数不代表外部实际处理数量。

验证 `python3 scripts/test-starter.py disruptor`：真实 HTTP 覆盖七种策略、SINGLE 多调用线程、
注解工作线程/虚拟线程、满载、原 void 发送、原生登记、指标删除重建及错误恢复。
原源码逐项映射与测试记录见 [MIGRATION.md](MIGRATION.md)。
