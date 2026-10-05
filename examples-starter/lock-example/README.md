# 锁示例

真实集成锁和多Redis Starter，默认本机18089端口，测试连接由TEST_REDIS_ADDRESS注入。

- GET `/api/lock/run`：key、type、permits、wait、delay、fail参数，返回进入临界区时共享活动数。
- GET `/api/lock/annotated?key=...`：通过服务代理执行@Lock。
- GET `/api/lock/active?key=...`：同步自动化对持有状态的等待。

自动化`python3 scripts/test-starter.py lock`会创建专用Redis和两个独立进程，验证本地/跨实例互斥、读锁共享、信号量额度、超初始租约续期、失败获取不释放、业务异常清理、注解及非法参数。最后停止进程并删除测试容器。
