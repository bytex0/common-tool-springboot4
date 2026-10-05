# SFTP

依赖 `io.github.bytex0:sftp-spring-boot-starter`。使用维护中的 mwiede JSch 2.28.7 和 Commons Pool。
配置 `sftp-pool.enable=true`、`host`、`port`、`username`、`known-hosts` 及 `password` / `private-key` 二选一。
主机公钥必须预先通过可信渠道确认，不提供关闭校验的开关。配置类不输出密码。

注入 `SftpTemplate`，使用 `upload(path, InputStream)`、`download(path, OutputStream)`、`list(path)`、`delete(path)`。
流属于调用方，调用结束前必须保持打开；大文件不进入堆，SDK 通道不逃逸作用域。
借用操作失败即销毁通道与 Session；成功归还。默认最多 4 个连接，
`connect-timeout` 覆盖连接与网络读取，`max-wait` 控制池等待，两者必须在 1ms 至 120s。
启动不连接远端，池关闭后拒绝新请求，已借出的连接在归还时销毁。

原实现审查：两个同类型 Bean、未初始化的默认池、只关闭通道泄漏 Session、断连对象不归还导致池耗尽、
凭据可能出现在 Lombok toString、未明确校验主机密钥。
本次统一资源所有权并修复上述问题。旧裸 borrow/return API 改为高层操作，
不再提供运行时命名池注册；多连接需求显式声明带限定名的独立 `SftpTemplate` Bean。
不承诺跨多操作事务，也不自动重试覆盖写入。

验证 `python3 scripts/test-starter.py sftp`：独立进程 Apache MINA SSHD、真实文件 SHA-256、
空文件、失败后连接池恢复及清理。不访问用户业务 SFTP。
