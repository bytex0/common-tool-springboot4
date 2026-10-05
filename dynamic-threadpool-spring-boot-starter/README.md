# 动态线程池

依赖 `io.github.bytex0:dynamic-threadpool-spring-boot4-starter`。
通过 `dynamic-threadpool.pools.<name>.core/max/capacity` 定义命名池，
注入 `ThreadPoolRegistry` 调用 `submit(name, Callable)`、`resize(name, core, max)`、`stats(name)`。
无配置不创建池；`dynamic-threadpool.enabled=false` 可关闭。
`TaskDecorator` Bean 可显式传播并清理业务上下文。

使用 Spring ThreadPoolTaskExecutor 管理执行和关闭。扩容先提高 max，缩容先降低 core；
先校验再修改，容量固定不假装动态生效。任务满时拒绝并计数，关闭时中断执行和取消排队 Future，
每个池最多等待 5 秒，业务必须响应中断。快照含实际核心/最大线程数、排队/活动/完成/拒绝数。

配置更新和关闭状态使用 `ReentrantLock`，不使用内置监视器锁；停止接收任务后先释放配置锁，
再等待线程退出，允许任务退出回调读取状态。并发更新、异常释放、装饰任务取消和关闭竞争均有回归测试。

原实现的问题包括：TTL 装饰器错误强转后静默回退、扩容更新顺序错误、优先队列实际无界、
不支持的容量变更仍写入配置、静态注册表及关闭不完整。新接口避免这些隐式行为。
兼容变化：固定有界队列，用标准 TaskDecorator 替代 TTL 强转；不接管 Tomcat/Hikari 业务池，
不迁入旧第三方池反射适配、消息告警和无效的配置中心自动刷新。
配置中心应在受控入口显式调用 resize，监控与告警可读取 stats。

验证 `python3 scripts/test-starter.py dynamic-threadpool`，覆盖真实线程、饱和拒绝、扩缩容和非法更新。
