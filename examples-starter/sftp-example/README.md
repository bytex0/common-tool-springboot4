# SFTP 示例

接口：`PUT/GET/DELETE /api/sftp/file/{name}`、`GET /api/sftp/files`。
限定文件名和 upload 目录，仅用于独立测试服务；不要向公网暴露该示例。
真实依赖 SFTP Starter，测试通过 Mockito 隔离远端，实际联调通过自动化另行完成。

运行 `python3 scripts/test-starter.py sftp`。脚本通过 sftp-fixture Profile 在独立 Java 进程中启动
Apache MINA SSHD，使用随机端口、临时账户密码和文件系统，生成主机公钥并构造临时 known_hosts，
验证上传下载并清理文件和两个进程。SSHD 依赖仅在示例中，库制品不引入服务器。
手动运行 JAR 时设置 `TEST_SFTP_ENABLED=true`、`TEST_SFTP_PORT`、`TEST_SFTP_USERNAME`、
`TEST_SFTP_PASSWORD`、`TEST_SFTP_KNOWN_HOSTS`，后者必须是可信公钥文件路径。
