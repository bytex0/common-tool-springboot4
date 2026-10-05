# 幂等 Starter

坐标：`io.github.bytex0:idempotent-spring-boot4-starter`。

在通过Spring代理调用的同步服务方法上使用`@Idempotent(key="#requestId", expire=10)`。不再要求方法同时带POST/PUT映射注解。默认启用，`idempotent.enabled=false`关闭。

```yaml
idempotent:
  enabled: true
  key-prefix: application:operation:
  default-expire-seconds: 10s
  debug-log: false
```

保留原注解默认值：`expire=10`、`keyPrefix="idempotent:"`。显式写 `expire<=0` 使用配置窗口，
`keyPrefix=""` 使用配置前缀；未指定时仍采用注解默认值。真正执行时必须有 RedissonClient，
可配合 `multi-redis-spring-boot4-starter`；单独添加依赖不会连接 Redis。
`debug-log=true` 仅记录 DEBUG 阶段和窗口，不记录业务键或参数。

- 使用watchdog处理锁防止正在执行的重复调用，成功后才写带TTL的完成标记。完成窗口从成功时开始，不与执行时间混用。
- 业务失败不写成功标记，并释放自己获取的锁，允许重试。重复获取失败不会解锁其他请求。
- 拒绝同线程同key嵌套幂等调用，不利用可重入锁重复执行业务。
- key为空时，使用方法签名和稳定排序的参数JSON生成SHA-256；不依赖toString、参数名拼接或应用出站脱敏模块。
- 显式key表达式必须返回稳定标量，推荐请求ID/业务单号。不要把上传流、Servlet对象作为默认指纹参数。
- 不缓存或回放业务响应，重复请求抛IdempotentException，示例映射409。
- 前缀和显式key需要包含应用、操作及租户边界，避免不同业务意外共享去重域。

本组件不提供跨Redis和数据库的exactly-once事务。业务已提交但写Redis标记失败、网络分区、进程暂停或外层事务尚未提交等情况，仍须通过同库幂等记录、唯一约束、事务或outbox处理。作用域必须覆盖业务提交；不要用于返回后仍运行的Future/响应式异步任务。丢失处理锁会报错，但不能撤销已经发生的业务副作用。

## 原 API 兼容

- 保留 `execute(String,long)` 独立检查及 `(RedissonClient, IdempotentProperties)` 构造器。
  检查成功即预占窗口，不能感知后续业务失败；新业务建议使用 `execute(key, duration, action)`。
- 同一版本、同一逻辑键下，两种执行器入口共享处理锁和完成标记，防止混用时重复放行。
  原入口继续在原键写时间戳；原入口的两次存储写入不是跨键事务，第二次写入失败会报错，
  原键可能仍已预占。Redis 出错时不要假定业务一定未执行。
- 保留键生成器无参构造和原 `generateKey(String,String,ProceedingJoinPoint,BeanFactoryResolver)`。
  该重载仍使用原前缀加 MD5 的协议，包括参数名加 `toString` 的默认行为。
  原自定义子类覆盖四参数方法时，默认新切面也会调用它；同时覆盖新方法则优先新方法。
- 自动配置的默认注解路径使用稳定 SHA-256 协议；原切面 `ApplicationContext` 构造器与
  `checkIdempotent` 方法继续保留，前者使用原键生成协议，但业务仍包裹在成功后标记的安全作用域内。
- 四种异常构造器及 `requestId/args` 访问器恢复，参数数组防御性复制，不采集冲突堆栈。

**滚动升级限制**：旧运行实例没有新处理锁/完成标记，默认注解键协议也不同。新旧二进制混跑不能保证
共享去重状态；应排空旧流量、等待旧窗口结束后切换，或先统一业务幂等记录。
调用方自行提供的键必须隔离命名空间，不要使用其他业务已有 Redis 键。

完整功能映射见 [MIGRATION.md](MIGRATION.md)。验证命令：

```bash
mvn --batch-mode --no-transfer-progress clean verify
python3 scripts/test-starter.py idempotent --skip-build
```
