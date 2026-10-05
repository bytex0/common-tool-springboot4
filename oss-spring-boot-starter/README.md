# OSS Spring Boot 4 Starter

基于 Java 21、Spring Boot 4 和 AWS SDK v2 的 S3 兼容组件。Java 包为 `io.github.bytex0.oss`，不再使用 AWS SDK v1。

## 接入

在使用方导入本项目 BOM 后添加：

```xml
<dependency>
    <groupId>io.github.bytex0</groupId>
    <artifactId>oss-spring-boot4-starter</artifactId>
</dependency>
```

```yaml
oss:
  enable: true
  endpoint: ${OSS_ENDPOINT}
  access-key: ${OSS_ACCESS_KEY}
  access-secret: ${OSS_ACCESS_SECRET}
  region: ${OSS_REGION:us-east-1}
  bucket-name: ${OSS_BUCKET}
  path-style-access: true
  chunked-encoding-disabled: true
  max-connections: 100
  connection-timeout: 10s
  socket-timeout: 60s
  api-call-timeout: 5m
  multipart-threshold: 16MB
  multipart-part-size: 8MB
```

默认 `oss.enable=false`，仅添加依赖不会创建连接。`endpoint` 必须是无桶路径、凭据或查询串的 HTTP(S) S3 API 地址。针对本地 S3 兼容服务使用路径风格与 SigV4；其他厂商需要按实际区域、端点和寻址风格配置，不保证所有厂商的扩展 API 一致。

`bucket-name` 供应用侧使用，`OssClient` 方法仍显式传入桶名称；Starter 不自动创建桶。可提供自定义 `AwsCredentialsProvider` 使用外部凭据链；自定义整个 `OssClient` 时默认客户端配置整体退让。

注入 `OssClient` 时使用构造器注入。底层 `S3Client` 和 `S3Presigner` 由 Spring 关闭，不要在单次业务调用结束后关闭共享客户端。

## 功能与资源约定

- 桶：创建、查询、删除空桶；删除桶不隐式删除已有对象。
- 普通上传：文件、未知长度流、显式长度流及进度回调。
- 流上传先临时落盘，使用准确长度和可重开文件流支持 SDK 签名与重试，不使用 `available()` 或全量读取到堆内存。临时文件在成功和失败后清理，需预留临时磁盘空间。
- 传入的上传 `InputStream` 由调用方关闭；从 `getObject` / `downloadObject` 返回的流也必须由调用方关闭。`ChunkDTO` 的 `MultipartFile` 输入流由组件关闭。
- 文件下载先写同目录临时文件，长度校验成功后替换目标文件；失败保留已有文件并清理临时文件。
- 进度回调同步执行，重试时重新计数；服务端确认成功或文件落盘成功后才报告 100%。回调应轻量且不要抛出异常。
- 文件和两种流入口在达到 `multipart-threshold` 后自动分片，恢复原 TransferManager 的大文件能力。
  默认 16MiB 阈值、8MiB 基础分片，逐片范围流读取且可重试，不把整个分片缓冲到堆；
  超大文件自动增大分片以控制在 10000 片以内。自动上传失败会取消本次任务。
- 显式分片接口仍用于业务侧断点续传。分片编号为 1 至 10000，除最后一片外通常至少 5MiB，
  合并前校验编号与 ETag 并排序。传输期间调用方不得修改源文件。
- 对象与分片列表自动遍历分页。`recursive=false` 使用 `/` 分隔符，仅返回本层对象，不将目录前缀伪装成文件。海量对象应直接使用原生 SDK 分页器逐页消费，避免聚合列表占用过多内存。
- 批量删除按每批 1000 个对象执行，部分失败会抛出异常，不把失败当成功。
- 下载预签名有效期为 1 秒至 7 天；URL 本身是临时访问凭证，不要输出到日志。
- 原 Map 更新入口继续保留其余标准头。新增 `updateObjectMetadata(bucket,key,HeadObjectResponse)`，
  可通过读取结果的 `toBuilder()` 修改 Content-Type、Cache-Control、Content-Disposition、
  Content-Encoding、Content-Language、Expires、网站重定向，以及存储类型和服务端加密设置。
  完整替换中 null 标准头表示删除，空 metadata 表示清空用户元数据；长度、修改时间等只读字段不写回。
- 元数据更新使用 ETag 条件自复制；显式传入的 ETag 是预期内容版本，否则先读取当前 ETag。
  ETag 不能检测所有仅元数据变化，不能当作跨系统事务。CopyObject 仍受服务端单次复制大小限制，
  SSE-C 密钥等额外授权材料不属于可读取的元数据。

## v1 到 v2 差异

| 原类型或行为 | 当前类型或行为 |
| --- | --- |
| `AmazonS3` | `S3Client` |
| `PutObjectResult` / `UploadResult` | `PutObjectResponse`，ETag 使用 `eTag()` |
| `InitiateMultipartUploadResult` | `CreateMultipartUploadResponse`，任务 ID 使用 `uploadId()` |
| `UploadPartResult` | `UploadPartResponse` |
| `PartETag` | `CompletedPart.builder().partNumber(...).eTag(...).build()` |
| `CompleteMultipartUploadResult` | `CompleteMultipartUploadResponse` |
| `S3ObjectSummary` | `S3Object` |
| `PartListing` | `List<Part>`，已遍历全部分页 |
| 下载 `S3Object` | `ResponseInputStream<GetObjectResponse>` |
| `ObjectMetadata` | 读取和完整更新使用 `HeadObjectResponse`；保留 Map 与可选类型重载 |
| `TransferManager` | SDK v2 同步范围流自动分片，保留大文件和进度能力，不注册 v1 TransferManager |
| v1 `ProgressEvent` 适配 | 同步流读取计数与成功确认；CustomProgressListener 的业务回调签名保持不变 |

`ChunkDTO` 保留 HTTP 分片参数，真正上传长度来自 `MultipartFile`。原来的分片大小、总大小、最后一片标志仅作为业务信息，不用于伪造请求长度。`ChunkMergeDTO` 接收 `List<CompletedPart>`；对外 HTTP JSON 使用示例中的独立 DTO，不直接序列化 SDK 类型。

## 验证

```bash
# 无需外部服务的单元测试
mvn -pl oss-spring-boot-starter -am test

# 已通过环境注入 OSS_ACCESS_KEY、OSS_ACCESS_SECRET 后执行真实接口联调
python3 scripts/test-starter.py oss

# 不使用业务服务，自动构建/缓存固定版本 MinIO 并运行隔离测试
python3 scripts/test-starter.py oss --local-oss
```

本地服务首次构建需要 Go 和网络，后续复用用户缓存；也可用 `TEST_OSS_BINARY` 指向已有 MinIO，
或通过 `TEST_OSS_IMAGE` 显式指定兼容测试镜像。构建器校验固定官方源码 SHA-256，
不会安装系统服务，测试凭据随机生成，仅传入子进程环境。普通 Maven 构建和 CI 不需要 Go 或 S3。

自动化真实启动 [OSS 示例](../examples-starter/oss-upload-examples/README.md)，只操作随机测试桶，
校验 SHA-256、三种入口的自动分片、手工续传、完整元数据、签名及资源清理。

**已知兼容限制**：本机 RustFS 镜像的 CopyObject 只更新用户元数据和内容类型，忽略其他标准头，
而同服务普通 PUT 能保存这些头。该版本不能通过完整元数据验收；不降级断言，不偷偷下载重传。
完整联调使用固定版本 MinIO，具体镜像标识及功能对照见 [MIGRATION.md](MIGRATION.md)。
