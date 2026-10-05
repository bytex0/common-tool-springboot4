# 文档 Starter 示例

真实集成 `docs-spring-boot-starter`，默认绑定 `127.0.0.1:18083`。手动运行需通过环境变量 `DOCS_USERNAME`、`DOCS_PASSWORD` 提供文档访问凭据。

- `GET /api/docs/ping`：不受文档 Basic 认证影响的业务示例。
- `GET /v3/api-docs`：带文档标题、版本和示例路径的 OpenAPI JSON。
- `GET /v3/api-docs.yaml`：受相同保护的 YAML 文档。
- `GET /swagger-ui/index.html`：Swagger UI。

```bash
python3 scripts/test-starter.py docs
```

脚本自动构建、启动、注入随机认证凭据，验证匿名/错误认证拒绝、认证后的真实 JSON 与 UI，再停止进程。普通 Maven 测试不需要环境中的真实凭据。
