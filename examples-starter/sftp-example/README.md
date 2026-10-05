# SFTP 示例

接口：`PUT/GET/DELETE /api/sftp/file/{name}`、`GET /api/sftp/files`。
限定文件名和 upload 目录，仅用于独立测试服务；不要向公网暴露该示例。
真实依赖 SFTP Starter，测试通过 Mockito 隔离远端，实际联调通过自动化另行完成。

命名池接口只复用测试服务身份，不接受请求传入远端地址或密码：

- `GET /api/sftp/pools`：列出名称。
- `POST/GET/DELETE /api/sftp/pools/{name}`：创建、统计和关闭命名池，不允许操作保留的 default 名称。
- `POST /api/sftp/pools/{name}/rename?source=...&target=...`：原借还通道接口执行重命名。
- `GET /api/sftp/pools/{name}/directory?change=true`：验证完整回调及归还后的目录重置。
- `POST /api/sftp/pools/{name}/disconnect`：断线后归还，验证关联 Session 同时关闭。
- `GET /api/sftp/pools/{name}/hold?delay=800`：有界持有，验证 100ms 池等待拒绝和在途关闭重建。

运行 `python3 scripts/test-starter.py sftp`。脚本通过 sftp-fixture Profile 在独立 Java 进程中启动
Apache MINA SSHD，使用随机端口、临时账户密码和文件系统，生成主机公钥并构造临时 known_hosts，
验证上传下载、命名池及通道生命周期，并清理文件、动态池和两个进程。
已经全量构建后可加 `--skip-build`，报告写入 `target/api-test-report.json`。
SSHD 依赖仅在示例中，库制品不引入服务器。
手动运行 JAR 时设置 `TEST_SFTP_ENABLED=true`、`TEST_SFTP_PORT`、`TEST_SFTP_USERNAME`、
`TEST_SFTP_PASSWORD`、`TEST_SFTP_KNOWN_HOSTS`，后者必须是可信公钥文件路径。
