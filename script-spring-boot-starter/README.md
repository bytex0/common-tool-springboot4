# 受信脚本

依赖 `io.github.bytex0:script-spring-boot-starter`，设置 `script.enabled=true`，
注入 `ScriptService` 调用 `execute("groovy", source, parameters)`。只允许业务提供受信源代码，
不能将用户提交的源代码直接交给此 API。

Groovy 版本由 Boot 管理。每次调用独立 Binding、Script 和可关闭类加载器，源内容变化立即生效。
默认 `parallelism=2`、`queue-capacity=16`、`timeout=5000` 毫秒，排队和编译也计时；
队列满拒绝，超时取消并中断，Groovy 循环注入中断检查。关闭时取消队列中的任务。
**这不是沙箱，也不能强杀忽略中断的 Java 方法**；不可信代码必须放在独立隔离进程，
参数中可变嵌套对象及返回对象仍属于业务，不能依赖浅拷贝隔离外部对象副作用。

原实现存在静态无界缓存、按 ID 命中旧源代码、共享 Script/Context 串参数、超时配置无效、
JavaScript 方法调用返回 null。本版不保留有状态编译对象缓存，优先保证隔离和类加载器关闭。
兼容变化：默认迁入完整 Groovy 执行路径；其他语言使用 `ScriptExecutor` Bean 扩展，
不捆绑原有未完善的 JavaScript/Lua/Jython/Java 执行器，不沿用旧缓存和 executeMethod API。
语言名称重复时启动失败。用户可覆盖默认 Groovy 执行器或整个服务。

验证：`python3 scripts/test-starter.py script`，覆盖真实运算、并发参数、超时恢复及固定脚本目录。
