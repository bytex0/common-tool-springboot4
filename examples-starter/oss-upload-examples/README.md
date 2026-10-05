# OSS Starter 测试示例

本模块通过 Maven 真实依赖 `oss-spring-boot4-starter`，使用自动配置注入 `OssClient`。它是本地测试接口，不是带权限控制的文件服务；默认只绑定 `127.0.0.1`，不要直接暴露到公网。

## 运行

通过环境提供配置，不把实际凭据写到 `application.yml`：

| 环境变量 | 用途 |
| --- | --- |
| `OSS_ENDPOINT` | S3 API 地址，默认 `http://127.0.0.1:19000` |
| `OSS_ACCESS_KEY` | 必填，访问密钥 ID |
| `OSS_ACCESS_SECRET` | 必填，访问密钥密码 |
| `OSS_REGION` | 签名区域，默认 `us-east-1` |
| `OSS_PATH_STYLE_ACCESS` | 路径风格，默认 `true` |
| `OSS_BUCKET` | 手动运行时必填的专用测试桶 |
| `SERVER_PORT` | 手动运行端口，默认 `18081` |

从仓库根目录运行：

```bash
mvn --batch-mode --no-transfer-progress clean verify
java -jar examples-starter/oss-upload-examples/target/oss-upload-examples-4.0.0-SNAPSHOT.jar
```

`GET /actuator/health` 用于确认就绪，应用不会自动创建桶。接口只操作 `OSS_BUCKET` 指定的桶，不接受调用方传入其他桶；上传请求单文件上限为 32 MiB，测试大文件请走分片。

## 测试接口

所有接口除原始下载外使用 `ApiResponse`，业务成功码为 `0`。参数校验错误返回 HTTP 400；S3 404 等错误保留状态码但不回传 SDK 请求细节。

| 方法 | 路径 | 参数或请求体 |
| --- | --- | --- |
| GET / POST / DELETE | `/api/oss/bucket` | 查询 / 创建 / 删除配置的空桶 |
| GET | `/api/oss/buckets` | 当前凭据可见的桶名称 |
| POST | `/api/oss/objects` | multipart `file`；查询参数 `objectName`，`mode=stream/progress/file` |
| GET | `/api/oss/objects` | `prefix`、`recursive`，默认递归 |
| GET | `/api/oss/download` | `objectName`；返回原始内容流 |
| GET | `/api/oss/download-progress` | `objectName`；返回落盘大小、SHA-256 及最终进度 |
| GET | `/api/oss/url` | `objectName`、`expiresSeconds`，默认 900 秒 |
| GET | `/api/oss/metadata` | `objectName` |
| PATCH | `/api/oss/metadata` | `objectName`、可选 `contentType`；JSON 用户元数据 Map |
| DELETE | `/api/oss/objects` | `objectName` |
| POST | `/api/oss/objects/delete-batch` | JSON 对象名称数组 |
| POST | `/api/oss/multipart` | `objectName`；返回 `uploadId` |
| PUT | `/api/oss/multipart/parts` | `objectName`、`uploadId`、`partNumber`；multipart `file` |
| GET | `/api/oss/multipart/parts` | `objectName`、`uploadId`；返回全部已上传分片 |
| POST | `/api/oss/multipart/complete` | JSON `objectName`、`uploadId`、`parts` |
| DELETE | `/api/oss/multipart` | `objectName`、`uploadId`；取消未完成的任务 |

合并请求的 `parts` 元素为 `{"partNumber": 1, "eTag": "上传返回值"}`，不是原生 SDK 对象。

## 自动化全流程

环境提前注入访问密钥后执行：

```bash
# 构建、运行单元和接口测试，再启动独立 JAR 做真实 HTTP 检查
python3 scripts/test-starter.py oss

# 仅在当前代码已完成 clean verify 后使用
python3 scripts/test-starter.py oss --skip-build
```

脚本自行选择随机端口并覆盖 `OSS_BUCKET` 为随机 `common-tool-it-*` 桶，不使用业务桶。检查包括：

- 健康检查及桶创建、查询、重复创建。
- 普通流、带进度流、文件三种上传，下载字节哈希与元数据长度比对。
- 空文件、中文及特殊字符对象名，递归与非递归查询。
- 元数据替换后内容不变，SigV4 预签名链接实际下载和有效期。
- 文件落盘下载进度及 SHA-256。
- 两片文件上传、查询已上传分片、乱序清单合并及内容比对。
- 取消分片任务并验证任务和对象均不存在。
- 错误有效期、空分片清单及缺失对象。
- 单个删除、批量删除及空列表。
- 清理测试桶、未完成任务、对象并停止示例进程。

每步输出 `PASS`，失败以非零退出码结束。报告写入 `target/api-test-report.json`，不包含凭据或预签名 URL。不对生产数据、生产桶策略进行操作。若外部服务不可用导致清理失败，脚本会报告本次测试桶名称供人工处理。

`src/test` 中的 MockMvc 测试用于普通构建，替换外部存储依赖；不能将其通过当作真实 S3 联调通过。每次 Starter 完成后都必须执行上述真实接口脚本。
