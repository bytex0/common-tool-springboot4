# 项目协作约定

本文件适用于整个 `common-tool-springboot4` 仓库。

## 沟通与范围

- 使用中文沟通、编写项目说明及 Git 提交信息。
- 本项目是 `common-tool` 的 Spring Boot 4.x 独立版本。迁移前先阅读原模块的实际代码、POM、配置及示例，不能仅按旧文档推测行为。
- 同级 `common-tool` 仓库仅作为迁移参考，未经要求不要修改。
- 保留已有未提交变更，不回退、覆盖或删除他人的工作。
- 控制修改范围，不顺带重构无关模块；未验证的迁移能力必须明确标注状态。
- 用户要求连续推进批次时，每完成一个 Starter 并提交后自动开始下一个，不在单个模块完成后结束工作或重复询问；外部阻塞需如实说明。
- 原代码只是功能参考，不视为正确实现。迁移前审查功能完整性、异常与边界、并发安全、资源生命周期、性能及配置隔离，不能只替换包名和依赖版本。
- 对发现的缺陷和缺失能力，先明确预期行为，再补实现及回归测试；接口不兼容或行为变化写入迁移说明。优化须针对实际问题，不为“重写”引入无关抽象。

## 工程规范

- Maven `groupId`、Java 根包名统一使用 `io.github.bytex0`。
- 主代码与测试目录分别使用 `src/main/java/io/github/bytex0`、`src/test/java/io/github/bytex0`。
- 移动包名时同时调整 `package`、`import`、测试、反射类名及自动配置注册资源。
- Java 基线为 21，Spring Boot 基线及构建配置以根 `pom.xml` 为准。
- 各 Starter 保持独立，使用 `xxx-spring-boot-starter` 命名，不强迫消费方引入无关中间件。
- 新增 Starter 同时更新根 POM 的 `modules` 和独立 BOM 的组件版本管理。
- Spring、Jackson、Lombok、JUnit 和构建插件版本优先由 Boot 统一管理，第三方版本集中在根 POM 管理。
- BOM 不继承根工程，避免循环导入；不得覆盖消费方选用的 Boot 版本。
- 只有示例模块启用 Boot `repackage`，库模块必须生成普通 JAR。
- 示例放在 `examples-starter` 下，不发布到远程仓库。

## Java 编码

- 新写代码不要使用 `@Autowired`。使用构造器注入或 `@Bean` 方法参数注入。
- 代码中的类型通过 `import` 导入，不在字段、方法签名或表达式中使用全限定类名。
- 新建类、接口、枚举及嵌套类型都要添加职责说明，作者统一为 `linshiqiang`。
- 类注释模板只用于新建类型，不批量修改已有类的注释或 `@since`。
- `@since` 必须填写创建时的实际日期和时间，不照抄示例时间。可执行 `TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M:%S'` 获取。

```java
/**
 * 功能名称(ExampleClass)职责说明
 *
 * @author linshiqiang
 * @since 2026-10-05 14:42:56
 */
```

上面的时间仅演示格式，新建类型时必须替换为实际时间。

- 实体、DTO、VO、配置属性等模型必须添加类说明。
- 字段注释使用多行 Javadoc，不写成单行；字段之间空一行。

```java
    /**
     * 单据号（文件名前缀，如核销单号或付款单号）
     */
    private String documentNo;

    /**
     * 原始文件名称
     */
    private String fileName;
```

- 使用标准库和既有辅助 API，避免不必要的抽象和复制粘贴。
- 资源所有权必须清晰：文件流、HTTP 连接和 SDK 客户端要正确关闭，临时文件必须清理。
- 涉及大文件时避免全量读入堆内存，不能将 `InputStream.available()` 当作文件长度。

## Spring Boot 4

- 自动配置使用 `@AutoConfiguration` 和 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 不使用 `spring.factories` 注册自动配置，不扫描整个项目根包。
- 默认 Bean 使用 `@ConditionalOnMissingBean`，可关闭能力使用 `@ConditionalOnProperty`。
- 外部服务连接应按需启用，不能因为引入 Starter 就访问数据库、Redis 或对象存储。
- Web MVC 使用 Boot 4 对应的模块与测试 Starter。
- JSON 使用 Jackson 3 的 `tools.jackson` API；注解仍使用 `com.fasterxml.jackson.annotation`。
- 不直接复用 Boot 3 专用依赖，迁入的第三方 Starter 必须核对 Spring 7 / Boot 4 兼容性。

## 安全与验证

- 不把真实 AK/SK、密码、令牌或签名 URL 写入源码、文档、测试固定数据、日志或提交信息。
- 联调凭据仅通过环境变量或未跟踪的本地配置传入；测试不能依赖开发者的真实凭据。
- 外部服务集成测试需显式启用。仅创建和清理本次测试专用资源，不修改已有业务桶、对象或访问策略。
- 新增 Starter 至少测试自动装配、关闭开关、用户 Bean 覆盖及关键操作。
- 普通构建和 CI 不依赖本地中间件、GPG 或发布凭据。
- 变更后运行适当测试；包名、父工程、BOM 或跨模块变更后运行全量验证：

```bash
mvn --batch-mode --no-transfer-progress clean verify
git diff --check
```

- 需要检查发布制品时使用 `mvn -Prelease verify`，未获明确要求不执行远程发布。
- 汇报实际验证结果和未完成事项，不把仅编译通过描述为已经联调成功。

## Git 提交

- 用户已授权：每完成一个 Starter，必须按下述流程自行测试，通过后执行本地 Git 提交，无需再次询问。
- 未经用户另行明确要求，不执行 `git push` 或远程发布。
- 提交说明要详细，采用中文模块标题与具体实现条目，不只写“修改代码”。

```text
feat(对象存储): 迁移 OSS Starter 至 Spring Boot 4

- 新增 io.github.bytex0 坐标下的 OSS 模块及自动配置注册
- 接入 AWS SDK v2，支持文件上传下载和分片续传
- 补充配置校验、资源关闭及分页处理
- 新增自动配置测试和显式启用的 S3 集成测试
- 更新接入文档与兼容性说明
```

更多模块规范参见 `docs/DEVELOPMENT.md`，迁移进展参见 `docs/MIGRATION.md`。

## 每个 Starter 的必做流程

以下流程适用于每一次新增、迁移或修改 Starter，不得只完成库代码或编译就结束：

1. 阅读原模块和当前实现，列出功能清单、已知不足、必要优化、兼容变化及外部服务依赖，再据此实现；尤其检查分页、过期、幂等、并发、失败清理及配置生效情况。
2. 实现 Starter，补充自动配置、配置校验、用户 Bean 覆盖及关键逻辑的单元测试。
3. 在 `examples-starter` 新建或更新对应示例模块，POM 必须真实依赖该 Starter，通过自动配置集成，不能复制 Starter 实现或绕过组件调用。
4. 在示例中编写测试接口，覆盖核心正常流程、参数错误和必要的失败分支；接口返回适合 JSON 序列化的 DTO 或模型，不直接暴露 SDK 内部对象。
5. 在示例的 `src/test` 编写接口测试，普通测试用替身隔离外部服务；真实集成必须另行实际运行。
6. 编写可重复运行的自动化接口测试脚本。脚本需自动构建和启动示例、等待健康就绪、发起真实 HTTP 请求、断言状态码和业务响应；文件或数据操作须比对内容，不只检查 HTTP 200。
7. 脚本通过环境变量接收外部服务参数和凭据，使用随机测试资源及空闲端口；无论成功还是失败，都必须尝试清理自己创建的资源并停止自己的进程，不能改动已有业务资源。
8. 自己执行全量 `mvn --batch-mode --no-transfer-progress clean verify`，再执行对应脚本进行真实联调；修改共享逻辑时同时回归受影响的 Starter。
9. 修复发现的问题并重跑，直到单元测试、示例接口测试和实际接口自动化全部通过。服务不可达、凭据缺失或测试失败时明确记录阻塞，不标记完成，不以跳过测试代替通过，不提交该 Starter 的未验证实现。
10. 更新 Starter README、示例运行方法、自动化命令和迁移说明，记录实际测试范围和结果。运行 `git diff --check` 并检查暂存内容没有密钥、签名 URL、构建产物或无关改动。
11. 测试通过后，主动执行详细的中文 Git 提交，只提交本次相关改动。在结果中报告示例、脚本、测试结果和提交号，不自动推送。

当前自动化入口为 `python3 scripts/test-starter.py <starter>`。新增 Starter 时扩展相应测试流程；默认构建后测试，只有确认当前代码已经构建时才使用 `--skip-build`。每次运行将不含凭据的检查报告写入对应示例的 `target/api-test-report.json`。
