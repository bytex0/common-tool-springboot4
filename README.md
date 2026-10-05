# Common Tool Spring Boot 4

[common-tool](https://github.com/kk01001/common-tool) 的 Spring Boot 4.x 独立版本，沿用 Maven 多模块和独立 Starter 结构。

本项目 Maven `groupId` 和 Java 根包名统一为 `io.github.bytex0`，源码目录对应 `io/github/bytex0`。

当前完成**项目框架、基础 Starter、OSS 和本地缓存 Starter**，不是原项目全部组件的完成迁移版。Redis、MyBatis-Plus、消息队列等组件尚未迁入，详见 [迁移说明](docs/MIGRATION.md)。后续已选定的 10 个常用组件及进度见 [第一批清单](docs/BATCH-01.md)。

## 技术基线

| 项目 | 版本或约定 |
| --- | --- |
| Java | 21，使用 `--release 21` 编译 |
| Maven | 3.9+ |
| Spring Boot | 4.0.8，先固定在 4.0.x 稳定分支 |
| Spring Framework、Jackson、Lombok、JUnit | 由 Spring Boot 统一管理 |
| JSON | Jackson 3；注解仍使用 `com.fasterxml.jackson.annotation` |
| 项目版本 | `4.0.0-SNAPSHOT`，区别于原仓库的 2.x 版本线 |
| 自动配置 | `@AutoConfiguration` + `AutoConfiguration.imports` |

## 工程结构

```text
common-tool-springboot4/
├── pom.xml                              # 聚合工程、Boot 基线、编译和测试约定
├── common-tool-springboot4-bom/          # 仅管理本项目组件版本，不覆盖使用方 Boot 版本
├── common-tool-spring-boot-starter/      # 基础自动配置、启动日志、ApiResponse
├── oss-spring-boot-starter/              # AWS SDK v2：上传下载、分片、签名和元数据
├── local-cache-spring-boot-starter/      # Caffeine：延迟初始化、隔离工厂、加载及统计
├── examples-starter/                    # 示例聚合工程，不发布到远程仓库
│   ├── common-tool-example/             # 可独立运行的 Web MVC + Actuator 示例
│   ├── oss-upload-examples/             # 真实集成 OSS Starter 的测试接口
│   └── local-cache-example/             # 本地缓存 CRUD、加载、过期及统计接口
├── scripts/test-starter.py              # 构建、启动、真实 HTTP 验证、清理和报告
├── docs/
│   ├── MIGRATION.md                     # 原项目分析与分批迁移清单
│   └── DEVELOPMENT.md                   # 新增 Starter 与代码约定
└── .github/workflows/ci.yml              # Java 21 全量构建与测试
```

基础 Starter 只引入 Spring Boot 基础运行依赖和 Jackson 注解，不隐式引入 Web 容器、数据库、Redis、Nacos、Excel 或 JSON 映射器。需要 Web 时由应用显式引入 `spring-boot-starter-webmvc`。

## 构建与运行

```bash
# 编译、测试并打包整个工程
mvn --batch-mode --no-transfer-progress clean verify

# 运行示例，无需数据库或 Redis
java -jar examples-starter/common-tool-example/target/common-tool-example-4.0.0-SNAPSHOT.jar

# 端口冲突时显式指定新端口
java -jar examples-starter/common-tool-example/target/common-tool-example-4.0.0-SNAPSHOT.jar --server.port=18080
```

默认示例接口：

- [GET /api/demo/ping](http://localhost:8080/api/demo/ping)：返回通用响应及应用状态。
- [GET /actuator/health](http://localhost:8080/actuator/health)：返回健康状态，仅暴露健康检查。

本地开发也可先执行 `mvn clean install`，再执行：

```bash
mvn -pl examples-starter/common-tool-example spring-boot:run
```

不要对整个聚合工程执行 `spring-boot:run`，只有示例模块有启动类。Starter 始终打包为普通 JAR，只有示例启用 Boot 可执行 JAR 插件。

## 接入方式

当前是本地开发快照，先在本仓库执行 `mvn clean install`。使用方需自行选用兼容的 Spring Boot 4.x 父工程或导入 Boot BOM，再导入组件 BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.bytex0</groupId>
            <artifactId>common-tool-springboot4-bom</artifactId>
            <version>4.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.bytex0</groupId>
        <artifactId>common-tool-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

无需扩大业务应用的组件扫描范围，也无需手动 `@Import` Starter 配置类。

```yaml
common-tool:
  enabled: true
  application-info-enabled: true
```

两个开关均默认为 `true`。关闭 `enabled` 后不注册基础设施 Bean，但仍可直接使用 `ApiResponse`。自定义 `ApplicationInfoInitialize` Bean 时，默认实现自动退让。

`ApiResponse` 保留 `code`、`message`、`request_id`、`ts`、`data` 字段和原有工厂方法，成功码仍为 `0`。注意 `ApiResponse.ok("text")` 沿用旧语义，表示请求 ID；返回字符串数据应使用 `ApiResponse.ok(requestId, "text")`。

## 测试与发布准备

- 基础 Starter 使用 `ApplicationContextRunner` 验证默认装配、属性开关和用户 Bean 覆盖。
- 使用 Jackson 3 验证响应模型序列化、反序列化和原有字段命名。
- 示例通过随机端口启动真实 HTTP 服务，验证自动配置发现、业务接口和健康检查。
- `mvn -Prelease clean verify` 额外生成源码包和 Javadoc 包，默认构建不需要 GPG 或发布凭据。
- 当前未配置远程发布和自动发布流程，不会在构建时上传制品。

每完成一个 Starter，必须编写对应示例、接口测试及自动化脚本，实际测试通过后提交代码，完整流程见 [AGENTS.md](AGENTS.md)。

```bash
# 自动构建工程、启动基础示例并测试接口
python3 scripts/test-starter.py common

# 本地缓存，无需外部中间件
python3 scripts/test-starter.py local-cache

# OSS_ACCESS_KEY、OSS_ACCESS_SECRET 由环境提前注入
# 默认连接 http://127.0.0.1:19000，可通过 OSS_ENDPOINT 修改
python3 scripts/test-starter.py oss
```

脚本使用 Python 3.10+ 标准库，无需安装第三方包；使用随机端口、随机测试桶，并自动清理。只有刚刚验证并打包了当前代码时才使用 `--skip-build`。结果写入对应示例的 `target/api-test-report.json`，失败返回非零退出码。

OSS 接入方式见 [组件文档](oss-spring-boot-starter/README.md)，接口列表见 [示例文档](examples-starter/oss-upload-examples/README.md)。

本地缓存接入与兼容变化见 [组件文档](local-cache-spring-boot-starter/README.md)，接口及自动化说明见 [示例文档](examples-starter/local-cache-example/README.md)。

新增模块请参考 [开发约定](docs/DEVELOPMENT.md)。

## 许可证

[Apache License 2.0](LICENSE)
