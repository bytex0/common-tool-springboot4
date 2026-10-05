# 接口文档 Starter

使用 Springdoc 3.0.x 对应 Spring Boot 4.0.x，替换旧 Knife4j Boot 3 集成。引入 `io.github.bytex0:docs-spring-boot4-starter`，Web 应用显式引入 `spring-boot-starter-webmvc`。

```yaml
swagger:
  enabled: true
  title: API文档
  description: API接口文档
  version: 1.0.0
  basic-auth: true
  username: ${DOCS_USERNAME}
  password: ${DOCS_PASSWORD}
```

- 原来的 `basic-auth` 仅声明 OpenAPI scheme，本次改为实际保护 JSON、YAML、UI 和文档静态资源，未认证返回 401。
- 不再提供默认用户名和密码，开启认证但缺少凭据时启动失败。
- Basic 认证不是加密，非本机访问必须通过 HTTPS；文档保护不替代业务接口认证。
- `swagger.enabled=false` 使文档路径返回 404，业务接口不受影响。
- 保留 title、description、version 和 contact 配置，用户自定义 `OpenAPI` Bean 时默认元数据退让。
- 支持 Springdoc 自定义文档/UI 入口和 Servlet 前缀。使用标准 Spring URL 路径解析处理编码及路径参数。
- UI 入口为 `/swagger-ui/index.html`，JSON 为 `/v3/api-docs`，不再提供旧 `/doc.html` 页面。
- 需要业务接口 security scheme 时由应用自行定义 OpenAPI，不把“文档认证”伪装为业务 API 已受保护。

示例：`examples-starter/docs-example`。自动化：`python3 scripts/test-starter.py docs`，脚本自动生成仅用于本次进程的认证凭据，验证拒绝匿名访问、正确认证、实际文档内容、UI 和业务接口隔离。
