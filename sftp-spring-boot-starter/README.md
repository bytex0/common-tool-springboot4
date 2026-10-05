# SFTP

依赖 `io.github.bytex0:sftp-spring-boot4-starter`。使用维护中的 mwiede JSch 2.28.7 和 Commons Pool。
配置 `sftp-pool.enable=true`、`host`、`port`、`username`、`known-hosts` 及 `password` / `private-key` 二选一。
主机公钥必须预先通过可信渠道确认，不提供关闭校验的开关。配置类不输出密码。

注入 `SftpTemplate`，使用 `upload(path, InputStream)`、`download(path, OutputStream)`、`list(path)`、`delete(path)`；
全部操作都有首参数为 `poolName` 的命名池重载。`execute(poolName, operation)` 支持完整通道操作，
例如目录、重命名、权限与属性读取。回调必须同步完成，不能返回通道或尚未关闭的远程流。
输入/输出流属于调用方，大文件流式处理。

借用操作失败即销毁通道与 Session，成功归还。默认最多 3 个连接；
`connect-timeout` 默认 3 秒，覆盖连接与网络读写；未设置 `max-wait` 时沿用原连接超时，
显式 `max-wait` 优先，两者必须在 1ms 至 120s。构造时不连接远端。

恢复 `max-idle`、`min-idle`、`min-evictable-idle-time`，默认分别为 3、1、5 秒，
实际最大空闲数不超过最大连接数。`eviction-interval` 默认 30 秒，显式启用池后后台维护可补充最小空闲数；
设置为 `0s` 可关闭维护，示例使用 `min-idle=0` 和 `eviction-interval=0s` 验证完全按需连接。
`max-pools` 默认 32，限制当前管理器注册的池数。

原实现审查：两个同类型 Bean、未初始化的默认池、只关闭通道泄漏 Session、断连对象不归还导致池耗尽、
凭据可能出现在 Lombok toString、未明确校验主机密钥。
本次统一资源所有权并恢复原有命名池能力：

- 保留 `core.SftpInfoProperties` 的 Builder、原十参数构造及所有原字段；增加可信主机文件和私钥选项。
- 保留 `JschFactory` 原构造和全部 PooledObjectFactory 方法，销毁时关闭通道及 Session。
  原未传 knownHosts 的构造方式使用标准 `~/.ssh/known_hosts`，不存在或不可信时明确失败，不提供关闭校验的后门。
- 保留 `JschConnectionPool` 无参/原参数构造、`buildPool`、两个 `getPool` 重载、
  `buildJschConnectionPool`、默认/命名 `borrowObject/returnObject` 及 `close(name)`。
- 原 `SftpConfiguration` 工厂类型可直接调用；自动配置统一提供一个管理器，
  `jschConnectionPool/defaultConnectionPool` 是同一 Bean 的两个名称，避免同类型注入歧义。
- `close(name)` 移除并关闭指定池，名称可重新注册。在途通道归还到借出时的实际池，不会污染同名新池。
  `close()` 修正为关闭全部命名池，不只关闭默认池。
- 断连通道归还执行销毁，不能遗留活动计数。成功归还时重置 `cd/lcd`，避免下个借用者继承工作目录。
- 原始借还须配对，同一通道不得并发使用。直接通过 `getPool` 使用 Commons Pool 时，应在同一原池上借还，
  不与管理器借还混用；独立 `getPool(properties)` 创建的未注册池由调用方关闭。

不承诺跨多操作事务，也不自动重试覆盖写入。

验证 `python3 scripts/test-starter.py sftp`：独立进程 Apache MINA SSHD、真实文件 SHA-256、
空文件、失败后恢复、命名池、原通道重命名、工作目录重置、Session 关闭、池耗尽和在途关闭重建。
不访问用户业务 SFTP。逐项对照见 [MIGRATION.md](MIGRATION.md)。
