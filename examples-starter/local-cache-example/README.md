# 本地缓存 Starter 示例

补充 `GET /api/cache/type-stats`，验证与原工厂类简单名根键一致的十三项统计数据。

示例真实依赖 `local-cache-spring-boot4-starter`，通过自动配置注册 `DemoCache`。无需外部中间件，默认只绑定 `127.0.0.1:18082`。

`DemoCache` 最大 100 项，访问后 2 秒过期，仅用于演示短期缓存，业务应用自行选择容量和过期时间。

## 接口

| 方法 | 路径 | 查询参数 |
| --- | --- | --- |
| PUT | `/api/cache/entry` | `key`、`value` |
| GET | `/api/cache/entry` | `key`，返回是否存在及值 |
| GET | `/api/cache/load` | `key`、`value`，未命中时加载并返回累计加载次数 |
| DELETE | `/api/cache/entry` | `key` |
| DELETE | `/api/cache/all` | 清空本示例全部缓存 |
| GET | `/api/cache/stats` | 返回真实命中、缺失、加载及淘汰统计 |

接口使用 `ApiResponse`；空白 key 返回 HTTP 400。缓存未命中返回 `present=false`，不自动读取其他数据源。

## 运行与自动化

```bash
mvn --batch-mode --no-transfer-progress clean verify
java -jar examples-starter/local-cache-example/target/local-cache-example-4.0.0-SNAPSHOT.jar
```

从仓库根目录执行自动化：

```bash
python3 scripts/test-starter.py local-cache

# 仅在当前代码已经完成构建后使用
python3 scripts/test-starter.py local-cache --skip-build
```

脚本自动构建、启动随机端口，验证健康检查、中文值 CRUD、加载复用、真实统计、访问过期、错误 key、清空与进程停止。测试报告位于 `target/api-test-report.json`，任何失败返回非零退出码。

`src/test` 中的接口测试也集成真实缓存和自动配置。更复杂的并发与过期边界由 Starter 单元测试覆盖。
