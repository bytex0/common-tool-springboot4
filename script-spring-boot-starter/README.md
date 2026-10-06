# 受信脚本

依赖 `io.github.bytex0:script-spring-boot4-starter`，设置 `script.enabled=true`，
注入 `ScriptService` 调用 `execute("groovy", source, parameters)`。只允许业务提供受信源代码，
不能将用户提交的源代码直接交给此 API。

Groovy 版本由 Boot 管理。每次调用独立 Binding、Script 和可关闭类加载器，源内容变化立即生效。
默认 `parallelism=2`、`queue-capacity=16`、`timeout=5000` 毫秒，排队和编译也计时；
队列满拒绝，超时取消并中断，Groovy 循环注入中断检查。关闭时取消队列中的任务。
**这不是沙箱，也不能强杀忽略中断的 Java 方法**；不可信代码必须放在独立隔离进程，
参数中可变嵌套对象及返回对象仍属于业务，不能依赖浅拷贝隔离外部对象副作用。

## 类型化迁移入口

原版包结构对应的新入口为 `io.github.bytex0.script.service.ScriptService`，
与已有根包服务并存，不改变根包 `execute("groovy", ...)` 的脚本体语义。
类型化服务提供 `execute(id, type, source, params)`、`executeMethod`、`refresh`、
`remove`、`validate`、`addExecutor`、`getExecutor`、`getSupportedTypes`。

- `GROOVY`：类型化入口默认调用 `execute(Map)`，指定方法接收一个 Map。
- `JAVA`：JDK 21 编译公开类，默认调用 `execute(Map)`，指定方法接收一个 Map。
  每次执行独立类加载器，编译文件使用随机目录，缓存退休后清理。
- `JAVASCRIPT`：GraalJS 24.1.2，默认执行脚本体，指定函数接收一个参数对象；
  返回常见 Java 数据而不是关闭后失效的 `Value`。
- `LUA`：LuaJ 3.0.1，默认执行脚本体，指定函数接收参数表；
  保留原版参数字符串转换和结果字符串协议。
- `PYTHON`：Jython 2.7.4，仅支持 Python 2.7；脚本体使用 `result` 变量返回结果，
  未定义时返回 null，指定函数接收 Map。修复原版直接调用代码对象的错误。

JavaScript、Lua、Python 是可选依赖，消费方选择所需引擎；示例显式引入全部引擎。
默认总开关关闭，各语言开关在总开关开启后生效：

```yaml
script:
  enabled: true
  parallelism: 2
  queue-capacity: 16
  timeout: 5000
  cache-size: 1000
  groovy:
    enabled: true
    cache-size: 100
  java-script:
    enabled: true
    strict-mode: true
    allow-host-access: false
  lua:
    enabled: true
    sandbox: true
  python:
    enabled: true
  java:
    enabled: true
```

缓存按源码、类型与执行器实例校验，不仅比较 ID。刷新失败保留旧条目；
删除、清空、淘汰及关闭不会释放仍被租约使用的编译产物。
原静态全局缓存改为实例缓存；直接使用编译器时由调用方调用 `release` 释放产物，
调用 `execute` 或 `executeMethod` 会自动释放临时产物。

所有脚本仅限受信代码。Lua 标准库限制与 JavaScript 默认不授予宿主访问权不构成安全沙箱。
需要恢复原版 JavaScript 的 Java 互操作时，显式设置 `script.java-script.allow-host-access=true`；
该开关授予全部宿主访问权，不能用于不可信脚本。Groovy 保留 Ivy 依赖解析能力，
编译阶段的 AST 转换及依赖解析也可能产生副作用，`validate` 不是无副作用的安全扫描器。
超时保证调用方等待有界，但 Java、Python、JavaScript 或第三方 Java 回调不一定响应线程中断；
这类任务可能继续占用工作线程，不得将该行为描述为强制终止或完整 CPU 隔离。
需要强制终止的不可信任务必须使用独立进程。

迁移仍在进行，公开缓存 API 兼容、全部配置组合和资源取消边界尚需继续验收，
详见 [逐项功能对照](MIGRATION.md)。不得仅以五语言正常调用通过标记完整迁移。

验证：`python3 scripts/test-starter.py script`，覆盖原有运算、并发参数、Groovy 超时恢复、
五语言指定方法、同 ID 源码变化、刷新删除与语法校验。实际结果见示例 target 下检查报告。
