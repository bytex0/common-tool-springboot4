# 开发约定

## 模块边界

1. 延续原项目扁平化的 `xxx-spring-boot-starter` 命名，每个 Starter 单独引入、单独测试。
2. Starter 继承根工程，不重复指定 Java、Spring、Jackson、Lombok 或测试框架版本。
3. 新模块同时加入根 POM 的 `modules` 和 `common-tool-springboot4-bom/pom.xml` 的版本管理。
4. BOM 保持独立，不继承根工程，不引入 Boot BOM，避免循环导入及覆盖消费端依赖版本。
5. 只有真实迁移并验证的模块才加入构建和 BOM，不发布空壳 Starter。
6. 可运行示例放入 `examples-starter`，继承其禁止远程部署的配置。
7. 不在基础 Starter 内堆积数据库、消息队列、Web、配置中心或文件处理依赖。

## 自动配置

- 使用 `@AutoConfiguration`，在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中逐行注册。
- 不使用 `spring.factories` 注册自动配置，不使用大范围 `@ComponentScan`。
- 配置属性用 `@ConfigurationProperties`；可关闭的功能使用 `@ConditionalOnProperty`。
- 默认实现使用 `@ConditionalOnMissingBean`，允许业务侧自定义覆盖。
- 对可选类库使用 `@ConditionalOnClass`，Web 配置增加对应的 Web 应用条件。
- 采用构造器注入或 `@Bean` 方法参数注入，新增代码不使用 `@Autowired`。
- 不把组件内部类作为业务扫描范围，也不让 Starter 启动外部连接后才能通过基础测试。

## Boot 4 约定

- Web MVC 使用 `spring-boot-starter-webmvc`，测试按需引入 `spring-boot-starter-webmvc-test`。
- JSON 映射器使用 `tools.jackson.databind.json.JsonMapper`，不新增 Jackson 2 Databind。
- Jackson 注解仍来自 `com.fasterxml.jackson.annotation`，不能批量替换整个 `com.fasterxml` 包名。
- 切面模块迁移时核对 `spring-boot-starter-aspectj`；不要直接复制旧 `spring-boot-starter-aop`。
- 自动配置类、测试注解等可能移到新的 Boot 模块，逐项以实际编译和运行结果为准。
- 非 Boot 管理的依赖在根工程统一管理版本，先核对其 Boot 4 / Spring 7 兼容性。
- 配置处理器和 Lombok 通过父 POM 的显式 annotation processor path 工作，模块中需要 Lombok 时仍声明 `optional` 依赖。

## Java 代码

- 新建类使用以下中文说明模板，`@since` 填写创建时的实际日期和时间；不批量改已有类注释。
- 模型及其字段必须有说明，字段注释使用多行 Javadoc，字段之间空一行。
- 类型使用 `import`，不在方法签名或代码中写全限定类名。
- 注释说明意图和限制，不重复解释明显的赋值逻辑。

```java
/**
 * 功能名称(ExampleClass)职责说明
 *
 * @author bytex0
 * @since 创建时的实际日期时间
 */
```

## 验证与版本

新增 Starter 至少覆盖默认装配、关闭开关、用户 Bean 覆盖；依赖可选类库时增加缺失类路径测试。对外暴露 HTTP、序列化或数据库行为时补充相应集成测试。

每次完成 Starter 必须执行 [AGENTS.md](../AGENTS.md) 中的“每个 Starter 的必做流程”：对应示例真实依赖 Starter，提供测试接口、示例接口测试和自动化脚本，自己调用真实 HTTP 接口验证完整流程，全部通过后主动执行本地 Git 提交，未经另行授权不推送。

```bash
mvn --batch-mode --no-transfer-progress clean verify
python3 scripts/test-starter.py common --skip-build
# 需提前注入 OSS_ACCESS_KEY 和 OSS_ACCESS_SECRET
python3 scripts/test-starter.py oss --skip-build
```

调整项目版本时同步所有模块的父版本、根工程与 BOM 的 `common-tool.version`，以及文档示例。当前组件统一使用 `io.github.bytex0` 作为 Maven `groupId` 和 Java 根包名，源码目录使用 `io/github/bytex0`，沿用原 `artifactId`，项目版本线为 `4.x`。迁移时同步调整 package、import、测试与自动配置注册资源，不混用旧仓库的 Boot 3 Starter。

`release` Profile 仅用于生成源码/Javadoc 制品。正式接入 Maven Central 前，再配置签名、发布凭据和人工触发的发布流程。不得把凭据放入 POM、YAML 或提交记录。

## 提交说明

使用中文说明具体模块与行为，标题后补充实现和验证细节，例如：

```text
feat(基础框架): 初始化 Spring Boot 4 多模块工程

- 建立根父工程与独立 BOM，统一 Java 21 和组件版本
- 新增基础 Starter 自动配置与配置属性元数据
- 新增独立 Web MVC 示例及健康检查
- 补充自动装配、Jackson 3 和 HTTP 集成测试
```
