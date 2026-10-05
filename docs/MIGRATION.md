# common-tool 迁移记录

## 当前验收状态

2026-10-05 已复核进入构建的 18 个 Starter，发现功能删减、API 兼容性和编码规范缺口。
下文保留阶段性实现与历史测试记录，**不能据此认定完整迁移**。
当前以 [迁移审查与整改清单](MIGRATION-AUDIT.md) 为准，先补齐已有模块，暂停新增 Starter。

## 原项目观察

本次参考同级目录 `common-tool` 的实际 POM、基础自动配置、响应模型、示例工程及发布工作流：

- 根工程坐标为 `io.github.kk01001:common-tool:2.4.9`，使用 Java 21、Spring Boot 3.5.8。
- 根 POM 启用 31 个 Starter 和 `examples`、`examples-starter` 两个示例入口；`push-spring-boot-starter` 被注释停用。
- Starter 直接位于根目录，按能力拆分；包名统一在 `io.github.kk01001` 下。
- 基础 Starter 同时声明 Web MVC、Nacos、EasyExcel、Fastjson、MyBatis、Hutool 等可选依赖。
- `CommonToolConfiguration` 使用整个 `io.github.kk01001` 的组件扫描，部分模块同时保留 `spring.factories` 与 `AutoConfiguration.imports`。
- 基础 `ApiResponse` 的成功码为 `0`，使用 `request_id` 与毫秒时间戳 `ts`，`ok(String)` 表示请求 ID 而不是数据。
- 根构建直接绑定源码、Javadoc、GPG 和 Central 发布插件，示例排除清单集中维护。

## 本次落地

| 部分 | 状态 |
| --- | --- |
| Java 21 + Spring Boot 4.0.8 父工程 | 已建立，Spring/Jackson/测试与构建插件版本交给 Boot 管理 |
| 独立组件 BOM | 已建立，仅管理真正落地的本仓库 Starter |
| 基础 Starter | 已建立，含 `CommonToolConfiguration`、配置开关、启动日志及 `ApiResponse` |
| 坐标与命名空间 | Maven groupId、Java 根包与源码目录统一为 `io.github.bytex0` |
| OSS Starter | 已迁入 AWS SDK v2，配套真实 Starter 示例、接口测试与自动化脚本 |
| 本地缓存 Starter | 已迁入并修正初始化时机、上下文隔离、统计与缓存命名，配套示例和自动化 |
| 自动配置发现 | 改为 `@AutoConfiguration` + imports，不扫描业务包 |
| 可运行示例 | 已建立，Web MVC + Actuator，无外部中间件依赖 |
| 测试与 CI | 自动配置、响应兼容、应用日志和真实 HTTP 集成测试 |
| 发布准备 | `release` Profile 生成源码/Javadoc；未配置签名与远程上传 |

基础 Starter 不是旧版本的完整替代品。ID 生成、操作日志、异常工具、事务工具、Nacos 等旧能力还未迁入。启动日志目前只输出应用名和环境，不输出未经确认存在的 Docs、Prometheus 等地址，也不输出配置中心地址。

## OSS 迁移与验证

OSS 迁移时重新读取的源模块已是 `io.github.archer099:common-tool:2.5.0`，使用 AWS SDK v1；上面的原项目观察保留首次搭建框架时的快照，不代表源仓库当前状态。

- 新模块使用 AWS SDK v2 `2.55.11`，版本由根工程导入的 SDK BOM 管理。
- 自动配置默认关闭，开启后校验连接配置；支持自定义 `OssClient`、SDK 客户端和凭据提供器，容器退出时关闭托管客户端。
- 保留常用操作方法名及 `oss.*` 配置前缀，Java 包统一改为 `io.github.bytex0.oss`，返回对象和分片类型改为 SDK v2，不是二进制兼容替换。
- 修正原实现的 `available()` 长度推断、流全量入堆、时区相关签名过期时间、查询只取第一页及批量删除限制。
- 文件与流单次 PUT、分片上传/续传/排序合并/取消、元数据替换、预签名链接及进度回调均已覆盖。
- 2026-10-05 本地验证：全量 50 个 Java 测试通过；基础示例 3 项真实 HTTP 检查、OSS 示例 15 项检查通过。OSS 使用 `http://127.0.0.1:19000`，随机测试桶及示例进程均已清理，真实凭据未写入仓库。
- 每个 Starter 后续均需按 AGENTS 的示例、接口、脚本、实际验证、提交流程执行。脚本报告位于示例 `target/api-test-report.json`，不纳入版本控制。

使用说明和 SDK v1/v2 类型差异详见 [OSS README](../oss-spring-boot-starter/README.md)。

## 本地缓存迁移与验证

- 使用 Boot 管理的 Caffeine 版本，移除未使用的 Hutool 和 Lombok 依赖。
- 保留抽象缓存类型及 get/put/remove/clear 方法，缓存不在父类构造阶段创建；Spring 工厂在单例完成初始化后注册并校验配置。
- 工厂不再使用静态方法、静态 Map 或静态 ApplicationContext，改为按上下文注入实例；退出时清空条目和注册表。
- 注册表以 Bean 名称为键，支持同类型多实例，按类型查询有歧义时明确报错。注册表和统计快照不可修改。
- 开启真实 Caffeine 统计，支持可控时钟验证过期行为；空值或失败加载不缓存，并发同键使用 Caffeine 原子加载。
- 2026-10-05：全量 68 项 Java 测试通过，缓存示例 8 项真实 HTTP 检查通过，基础示例 3 项和 OSS 15 项回归通过。测试进程和资源均已清理。

兼容性变化和接入方式详见 [本地缓存 README](../local-cache-spring-boot-starter/README.md)。本轮已选 10 个常用模块，逐个审查和验证，进度以 [第一批清单](BATCH-01.md) 为准。

## 后续迁移分组

第二批连续迁移清单见 [BATCH-02](BATCH-02.md)，各项验证通过后单独更新状态。

第一批现有用例曾通过测试，但完整功能与规范验收尚未通过，更正状态见 [BATCH-01](BATCH-01.md)。
最近一次限流整改后的全量构建为168项Java测试通过，限流双实例真实检查12项通过。
这不是其他模块的原功能覆盖率；限流逐项证据见其 [功能对照](../rate-limiter-spring-boot-starter/MIGRATION.md)。
类注释作者统一为bytex0，原有since时间保持不变。

下表保留最初推荐分组，不代表当前实现或验收状态。部分模块已开发但仍需补齐，
具体以审查清单和两批状态表为准：

| 批次 | 原模块 | 主要检查点 |
| --- | --- | --- |
| 1：轻依赖能力 | `design-pattern`、`disruptor`、`ip2region`、`sensitive-word` | Spring 7 接口、切面 Starter、资源与依赖边界 |
| 2：JSON 与 Web | `crypto`、`signature` | Jackson 3 扩展 API、MVC 自动配置新包名 |
| 3：Redis 与并发控制 | `multi-redisson` | 与已迁移multi-redis的能力边界及冗余评估 |
| 4：数据库与消息 | `mybatis-plus-spring3`、`local-message`、`dynamic-mq`、`mqtt`、`netty` | Boot 4 专用集成、动态数据源/事务兼容、消息连接生命周期 |
| 5：任务与容错 | `xxl-job`、`dynamic-threadpool`、`resilience4j` | Boot 4 Actuator 模块、Tomcat 11、Spring 7 适配 |
| 6：文件与外部服务 | `sftp`、`robot-message`、`script`、`ffmpeg` | SDK/JDK 21 兼容、资源关闭、可选依赖及外部服务测试 |
| 单独评估 | `push` | 原项目未启用，先确认是否继续维护 |

表格中的原模块名称沿用参考仓库命名。迁入本仓库的 Starter 坐标统一使用 `-spring-boot4-starter` 后缀。
原 `mybatis-plus-spring3-boot-starter` 迁入时应使用 `mybatis-plus-spring-boot4-starter`，
待对应依赖兼容性核实后再加入工程。

## 必须逐项核对

1. **Jackson 3**：默认 Databind 包名为 `tools.jackson`，注解包仍是 `com.fasterxml.jackson.annotation`。不能只升级版本而保留旧序列化扩展。
2. **模块拆分**：Web、Actuator、测试自动配置存在新的模块边界和包名，按需引入 Starter。
3. **切面依赖**：使用 Boot 4 的 AspectJ Starter，不直接复用旧 AOP Starter 声明。
4. **三方 Starter**：MyBatis、动态数据源、Redisson、Resilience4j、Knife4j、Spring Cloud Alibaba 必须各自核实兼容范围。当前不导入旧 Alibaba BOM。
5. **Servlet 容器**：Boot 4 默认 Web 栈涉及 Jakarta Servlet 6.1 / Tomcat 11，过滤器和容器线程池适配需要测试。
6. **资源发现**：自动配置 imports、MyBatis XML、Lua 脚本、字典与本地数据文件必须进入实际 JAR。
7. **制品类型**：仅示例使用 Boot repackage；库模块必须是可以普通依赖的 JAR。
8. **基础设施隔离**：新增模块不能让不使用该能力的应用连接数据库、Redis、Nacos 或消息队列。

## 参考

- [Spring Boot 4.0 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 JSON 支持](https://docs.spring.io/spring-boot/4.0/reference/features/json.html)
- [Spring Boot 自动配置开发](https://docs.spring.io/spring-boot/4.0/reference/features/developing-auto-configuration.html)
- [Spring Boot 版本元数据](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml)
