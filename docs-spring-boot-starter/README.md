# 接口文档 Starter

使用 Springdoc 3.0.x 对应 Spring Boot 4.0.x，保留 Knife4j 页面和增强功能。
引入 `io.github.bytex0:docs-spring-boot4-starter`，Web 应用显式引入 `spring-boot-starter-webmvc`。
Knife4j 升级到 4.5.0，明确排除其旧 Springdoc 依赖，由本组件桥接 Springdoc 3 的接口差异。

```yaml
swagger:
  enabled: true
  title: API文档
  description: API接口文档
  version: 1.0.0
  basic-auth: true
  username: ${DOCS_USERNAME}
  password: ${DOCS_PASSWORD}
knife4j:
  enable: true
springdoc:
  api-docs:
    version: OPENAPI_3_0
```

- 原来的 OpenAPI `basicAuth` scheme 已保留；同时实际保护 JSON、YAML、两种 UI 和文档静态资源，未认证返回 401。
- 不再提供默认用户名和密码，开启认证但缺少凭据时启动失败。
- Basic 认证不是加密，非本机访问必须通过 HTTPS；文档保护不替代业务接口认证。
- `swagger.enabled=false` 使文档路径返回 404，业务接口不受影响。
- 保留 title、description、version 和 contact 配置，用户自定义 `OpenAPI` Bean 时默认元数据退让。
- 支持 Springdoc 自定义文档/UI 入口和 Servlet 前缀。使用标准 Spring URL 路径解析处理编码及路径参数。
- 保留原 `/doc.html` Knife4j 页面，同时提供 `/swagger-ui/index.html`；JSON 为 `/v3/api-docs`。
- 保留原 `@ApiSupport`、`@ApiOperationSupport` 排序、分组、设置和 Markdown 扩展。
  标签排序改用已注册 MVC 方法，避免扫描时初始化无关类。
- 原 `knife4j.basic.enable` 也可使用，但必须显式配置用户名/密码；两套 Basic 同时开启时凭据必须一致。
- 原 `knife4j.production=true` 会对新旧页面、JSON、YAML 和分组统一返回 403，不再只屏蔽页面。
- 安全配置在过滤器创建时冻结，修改访问策略或凭据需重建 Bean/重启，避免可变配置与凭据不同步。
- 上游默认增强器按确切 Bean 来源适配；用户自定义 OpenAPI/增强 Bean 不会被覆盖。
  自定义增强推荐使用 Springdoc 3 接口；继承旧 Knife4j 增强器并调用旧父类实现时，需改用本组件的 `Knife4jBoot4Customizer`。
- 保留显式 `@EnableKnife4j` 用法，但通常自动配置已经足够；旧注解包含 Web 条件，放在启动类时测试应显式指定 `@SpringBootTest(classes=...)`。
- 业务接口认证仍由业务系统负责，OpenAPI scheme 声明不等于业务路由已经受保护。

## CORS

`knife4j.enable=true` 且 `knife4j.cors=true` 开启原跨域能力。
修复上游“通配 Origin + 凭据”在 Spring 中报错的配置：默认允许无凭据跨域。
需要 Cookie/凭据时显式配置：

```yaml
swagger:
  cors-allowed-origins:
    - https://docs.example.test
  cors-allow-credentials: true
```

携带凭据不允许通配 Origin。文档预检交给 CORS 策略处理，实际 GET 请求仍须通过认证。
CORS 不是业务认证或 CSRF 防护。

示例：`examples-starter/docs-example`。自动化：`python3 scripts/test-starter.py docs`，生成临时认证凭据，
验证页面和实际资源、分组及 Markdown，并启动独立进程验证 CORS/生产模式，结束后清理全部进程。
库与示例接入 Checkstyle。功能对照和差异见 [MIGRATION.md](MIGRATION.md)。
