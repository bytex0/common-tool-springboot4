# 幂等 Starter

在通过Spring代理调用的同步服务方法上使用`@Idempotent(key="#requestId", expire=10)`。不再要求方法同时带POST/PUT映射注解。默认启用，`idempotent.enabled=false`关闭。

```yaml
idempotent:
  enabled: true
  key-prefix: application:operation:
  default-expire-seconds: 10s
```

注解expire<=0使用默认窗口，keyPrefix为空使用配置前缀。真正执行时必须有RedissonClient，可配合multi-redis Starter；单独添加依赖不会连接Redis。

- 使用watchdog处理锁防止正在执行的重复调用，成功后才写带TTL的完成标记。完成窗口从成功时开始，不与执行时间混用。
- 业务失败不写成功标记，并释放自己获取的锁，允许重试。重复获取失败不会解锁其他请求。
- 拒绝同线程同key嵌套幂等调用，不利用可重入锁重复执行业务。
- key为空时，使用方法签名和稳定排序的参数JSON生成SHA-256；不依赖toString、参数名拼接或应用出站脱敏模块。
- 显式key表达式必须返回稳定标量，推荐请求ID/业务单号。不要把上传流、Servlet对象作为默认指纹参数。
- 不缓存或回放业务响应，重复请求抛IdempotentException，示例映射409。
- 前缀和显式key需要包含应用、操作及租户边界，避免不同业务意外共享去重域。

本组件不提供跨Redis和数据库的exactly-once事务。业务已提交但写Redis标记失败、网络分区、进程暂停或外层事务尚未提交等情况，仍须通过同库幂等记录、唯一约束、事务或outbox处理。作用域必须覆盖业务提交；不要用于返回后仍运行的Future/响应式异步任务。丢失处理锁会报错，但不能撤销已经发生的业务副作用。

旧“先execute检查、再调用业务”的API改为完整`execute(key, duration, action)`作用域。默认文本参数重载和MD5拼接不再使用。真实验证：`python3 scripts/test-starter.py idempotent`。
