# OSS 功能对照

阅读同级 `common-tool/oss-spring-boot-starter` 的 9 个 Java 文件、POM、配置和原 TransferManager 行为。
发布坐标为 `io.github.bytex0:oss-spring-boot4-starter`，SDK v1/v2 不是二进制兼容替换。

| 原文件及能力 | 当前实现或明确类型映射 | 验证 |
| --- | --- | --- |
| `OssClient/S3OssClient` 三个桶操作 | 保留 createBucket/getAllBuckets/removeBucket，遍历全部列表分页 | 真实创建、查询、重复创建、删除 |
| 两个输入流上传重载 | 保留内容类型和默认 MIME，真实长度落盘，调用方仍拥有输入流 | 错误长度、流所有权、真实内容对比 |
| 文件上传与带进度文件/流上传 | 同名重载保留，自动范围分片恢复 TransferManager 大文件能力 | 范围重试/失败取消单测、三种 20MiB HTTP 上传 |
| 手工分片入口与续传 | SDK v2 响应/CompletedPart 替换原类型，列表遍历分页，合并排序 | 分片分页单测、续传/乱序合并/取消 HTTP |
| getObject/downloadObject 两种下载 | 返回调用方关闭的流；文件下载先临时落盘再替换，失败保留原文件 | 截断检测、内容摘要、最终进度 |
| getObjectUrl | SigV4、1 秒至 7 天、精确时长校验 | 实际签名链接下载，URL 不写日志 |
| removeObject/removeObjects | 保留单删及批删，1000 个一批，部分失败明确报错 | 单测 2001 对象分组、真实删除 |
| getAllObjectsByPrefix、getContentType、getS3Client | 递归/非递归查询、原 MIME 类型和额外常用类型、原生客户端访问 | 分页与分隔符、实际特殊字符路径 |
| getObjectMetadata/updateObjectMetadata | ObjectMetadata 对应 HeadObjectResponse，恢复完整标准头更新并保留 Map 重载 | 复制条件、存储/加密参数单测、标准头 HTTP |
| `ChunkDTO/ChunkMergeDTO` | 原业务字段保留，SDK ETag 类型转换；真实分片长度不采用业务声明伪造 | JSON ETag 绑定、分片排序与校验 |
| `CustomProgressListener/ProgressListenerAdapter` | 业务回调签名保留；SDK v1 事件改为同步读取计数及确认事件 | 成功前不报告 100%、分片重试不重复计数 |
| `OssUtil` 三个静态入口 | 保留按时归档、对象拼接和 URL 构造；修正边界斜杠与编码 | 工具测试、中文路径实际上传 |
| `OssProperties/OssConfiguration` | 原配置前缀/字段保留，endpoint 为 URI；默认关闭外部服务、统一超时和分片策略 | 开关、覆盖、凭据来源、配置校验和容器关闭 |

## 优化和边界

- 自动分片不全量读入内存；输入流先落盘，文件按范围重新打开，正常 I/O/SDK 失败取消本次自动任务。
- 同步分片顺序传输以限制资源使用；不是原 v1 TransferManager 线程池的实现级复刻。
- 源文件传输期间不得修改。进程崩溃可能留下未完成任务，生产桶仍应配置未完成分片的生命周期清理。
- 进度适配器属于一次同步传输，不应被业务在多个传输中并发复用。
- 元数据复制保留存储和服务端加密参数；对应 KMS 权限必须由业务提供。本轮未使用真实 KMS 联调。
- ETag 条件保护内容版本，不保证检测仅元数据修改；服务端单次 CopyObject 大小限制仍适用。
- SDK 版本与寻址风格不同需要按 README 迁移，不能声称对所有 S3 厂商和扩展配置均已验证。

## 服务兼容性

2026-10-06 对本机 RustFS 镜像
`rustfs/rustfs@sha256:69e7d8168b9285bf0b95c9d98309c599704d17c0aa9a8614fbee376c0c43e5a1`
做独立实例对照：普通 PUT 保存 Cache-Control/Content-Disposition/Content-Language，
CopyObject 更新则丢失这些标准头，只有用户元数据与内容类型生效。完整断言失败后资源已清理，
没有把该行为隐藏为通过，也没有对已有业务实例做改动。

完整验证使用从官方 `RELEASE.2025-09-07T16-13-09Z` 源码构建的独立 MinIO。
`scripts/build-oss-fixture.py` 校验固定归档摘要，制品在用户缓存复用；
测试实例使用随机凭据、随机端口与临时数据目录，普通 Maven 构建不依赖 Go。

## 验证记录

2026-10-06 验证通过：

- 全量 `mvn --batch-mode --no-transfer-progress clean verify`：233 项 Java 测试，无失败/跳过；
  OSS Starter 28 项，示例 MVC 7 项。
- `python3 scripts/test-starter.py oss --local-oss --skip-build`：默认构建缓存入口实际运行，
  19 项检查全部通过，包含标准头、三种自动分片入口、手工续传、签名、摘要和资源清理。
- `python3 -m unittest discover -s scripts -p 'test_*.py'`：9 项通过，包含归档摘要错误不执行、
  已有制品不重复联网、缺失编译器失败，以及原坐标规则测试。
- 18 个自有制品、BOM/示例坐标校验通过；Checkstyle 覆盖源码、测试和示例，人工复核原 API、
  字段与方法注释、流所有权、异常清理、服务端标准头实际生效情况。
- 本次测试桶、对象、未完成任务、应用/S3 进程和临时数据已清理；只保留可复用的测试服务构建缓存。

本轮功能对齐与验收通过。RustFS 指定镜像的 CopyObject 标准头限制仍客观存在，
不表示该版本已通过完整兼容性验证，也不宣称 KMS 等外部授权路径已真实联调。
