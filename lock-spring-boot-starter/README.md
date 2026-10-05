# 锁 Starter

提供`@Lock`和`LockTemplate.execute(LockRule, action)`，默认启用，本地策略不需要Redis。通过`lock.enabled=false`关闭自动配置。使用Spring代理调用注解方法，自调用和非代理静态方法不生效。

支持本地可重入锁/公平信号量，Redisson互斥、公平、spin、读、写锁以及可过期信号量。`REDISSON_READ_WRITE_LOCK`按写锁处理；旧`REDIS_TEMPLATE_SEMAPHORE`迁移为Redisson令牌信号量，不再维护另一套不安全释放脚本。Redis策略需要应用提供RedissonClient，可配合multi-redis Starter。

- 只在成功获取后释放，业务异常保留原异常并附加清理异常。
- 本地注册表对持有者和等待者计数，最后一个引用离开即清理，不静态持有历史key。
- block=true最多等待timeout，false立即尝试；timeout明确是等待时间，不是租约。
- Redis锁leaseTime=0使用watchdog；显式租约丢失后报错，不错误解锁其他持有者。
- Redis信号量使用许可ID、自动续租和空闲TTL；额度冲突报错，失败获取不会增加许可数。
- Builder默认值明确保留。支持可信key/permitsFunction/ruleFunction SpEL，#p0/#a0及参数名可用。空key表达式使用方法签名隔离，字面量需加SpEL引号。
- 不接受用户输入作为表达式，参数值不会被当作表达式再次执行。Redis key对业务键做SHA-256，不日志输出原始键。
- 分布式锁不能替代数据库唯一约束、事务或fencing；网络分区、进程暂停和租约丢失仍须业务层处理。模板是同步作用域，不用于返回后仍继续执行的异步任务。

原分散的lock/tryLock/unlock工厂接口改为完整执行作用域，避免调用方遗漏释放或无所有权释放。关闭模板只停止自己的续租线程，不关闭应用共享Redis客户端。

验证：`python3 scripts/test-starter.py lock`，创建专用Redis及两个示例进程做跨实例并发检查后全部清理。
