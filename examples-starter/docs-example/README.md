# 文档 Starter 示例

真实集成 `docs-spring-boot4-starter`，默认绑定 `127.0.0.1:18083`。手动运行需通过环境变量 `DOCS_USERNAME`、`DOCS_PASSWORD` 提供文档访问凭据。

- `GET /api/docs/ping`：不受文档 Basic 认证影响的业务示例。
- `GET /v3/api-docs`：带文档标题、版本和示例路径的 OpenAPI JSON。
- `GET /v3/api-docs.yaml`：受相同保护的 YAML 文档。
- `GET /swagger-ui/index.html`：Swagger UI。
- `GET /doc.html`：原 Knife4j UI，使用真实静态资源。
- `GET /v3/api-docs/swagger-config` 和 `/v3/api-docs/sample`：分组发现及包含 Markdown 的分组文档。

```bash
python3 scripts/test-starter.py docs
```

脚本自动构建、启动、注入随机认证凭据，验证匿名/错误认证拒绝、认证后的真实 JSON、Markdown、
两种 UI 及资源，再启动独立 CORS/生产模式进程验证访问边界，最后停止所有进程。
普通 Maven 测试不需要环境中的真实凭据。
