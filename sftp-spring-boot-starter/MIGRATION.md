# SFTP 功能对照

参考同级 `common-tool/sftp-spring-boot-starter` 的 4 个 Java 文件与 POM，
保留命名池和通道操作成品能力，不能用单池文件 API 替代原动态管理。

| 原符号 | 当前实现与必要变化 | 验证 |
| --- | --- | --- |
| `SftpInfoProperties` 原字段、Builder、无参/全参数构造 | 原类型继承安全属性类，原十参数构造保留，构建器描述隐藏凭据 | 属性与构造测试 |
| connectTimeout 同时控制借用等待 | 未设置 maxWait 时沿用 connectTimeout，显式 maxWait 优先 | 默认与优先级测试 |
| maxTotal/minIdle/maxIdle/minEvictableIdleTime | 恢复配置并启用可关闭的维护周期，构造不预热 | 池容量真实统计、按需连接 |
| `JschFactory(host,port,user,password)` 与工厂五方法/销毁重载 | 原入口保留，标准 known_hosts 兜底；新配置构造支持指定信任文件和私钥 | 实际公钥拒绝与恢复 |
| 原通道创建和销毁 | 通道与 Session 共同归属，创建失败清理，归还重置 cd/lcd | 真实 Session 已关闭、目录重置 |
| `JschConnectionPool` 无参/原九参数构造 | 保留空管理器与默认池构造，增加安全配置构造和注册容量 | 原属性工厂、默认池启动测试 |
| buildPool/getPool/buildJschConnectionPool | 全部保留，动态注册幂等；独立创建的池由调用方关闭 | 真实命名池创建及重复创建 |
| borrowObject/returnObject 默认和命名重载 | 保留原通道入口；校验实际归属，断连归还销毁 | 重命名 HTTP、断连恢复、错误归还单测 |
| close(name)/close() | 移除单池允许重建；整体关闭全部池，修复原仅关默认池 | 活跃借用期间关闭重建、原池归还测试 |
| `SftpConfiguration` 两个工厂方法 | 原类型和直接调用方法保留；自动装配用别名合并同类型双 Bean | 原 Bean 名称及类型覆盖测试 |
| 已有 SftpTemplate 文件接口 | 全部保留并增加命名重载及完整同步回调 | 2MiB 内容摘要、空文件、错误恢复 |

## 资源与安全

- 原接口缺少可信主机参数时使用标准 known_hosts，不恢复原不明确的主机校验行为。
- 不向日志或接口输出配置密码，Lombok 构建器的字符串描述也做隐藏处理。
- 管理器锁只保护注册和移除，不持锁连接、等待池或调用用户回调。
- 原始通道必须借还配对，不并发使用；直接使用底层池不能与管理器借还混用。
- 关闭不强行中断已经借出的操作，归还时仍使用原池引用并销毁；用户不能永久遗留通道。
- 后台维护是显式启用连接池后的配置行为；构造不连接，默认 Starter 仍关闭。
- 公开操作作用域使用自动资源关闭，业务异常和清理异常均保留，不能返回仍持有远端资源的流或通道。

## 验收记录

2026-10-06 验证通过：

- 全量 `mvn --batch-mode --no-transfer-progress clean verify`：239 项 Java 测试通过，无失败/跳过；
  SFTP Starter 7 项，示例 MVC 2 项。
- `python3 scripts/test-starter.py sftp --skip-build`：12 项真实协议检查通过，包括命名池原借还、
  目录重置、断连后 Session 关闭、池耗尽、在途关闭重建，以及原上传下载/主机信任/错误恢复。
- SSH 服务与应用分别在独立 Java 进程运行，测试文件、动态池和两个进程均已清理。
- 本模块主代码、测试、示例 Checkstyle 通过；人工核对原接口、字段/方法注释、资源所有权、
  配置安全及构建器字符串隐藏。无 synchronized、Autowired 或通配符导入。
- 18 个自有库制品、BOM 和示例坐标检查通过。

原命名池和通道能力已恢复并验证；公钥校验、关闭语义及原始资源调用约束需按 README 迁移。
