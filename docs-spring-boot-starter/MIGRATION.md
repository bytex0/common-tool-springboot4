# 文档 Starter 功能对照

| 原能力 | 当前实现 | 验证 |
| --- | --- | --- |
| SwaggerProperties 展示字段、开关和访问器 | 保留原 Boolean getter/setter、相等性，toString 不输出凭据 | 配置与 JavaBeans 单测 |
| title/description/version/contact | OpenAPI Info/Contact 原样设置，用户 Bean 优先 | 自动配置与真实 JSON |
| 原 Basic security scheme | 恢复 basicAuth/HTTP/basic 声明，不伪装成业务认证 | MVC 和 HTTP 验证 |
| Knife4j doc.html 和完整资源 | 使用实际 4.5.0 UI JAR，不用重定向或简化页替代 | 页面、JS/CSS 真实加载及认证 |
| 原分组和设置扩展 | 保留上游配置对象及扩展协议，适配 Springdoc 3 分组集合返回类型 | 分组发现、JSON 请求 |
| Markdown 文档 | 使用原 OpenApiExtensionResolver，内容随文档输出 | 固定内容比对 |
| ApiSupport 标签排序 | 活动控制器元数据、包模式和继承注解，冲突按类名确定顺序 | 标签 x-order |
| ApiOperationSupport 接口排序 | 保留上游操作扩展器 | 接口 x-order |
| 显式 EnableKnife4j | 保留，在 Bean 初始化后精确适配默认扩展器，不依赖配置导入顺序 | 示例显式启用 |
| 用户自定义 OpenAPI/增强 Bean | 不覆盖用户工厂或子类 | 用户 Bean 行为单测 |
| 原 knife4j.basic 配置 | 支持且要求显式凭据，保护自定义文档路径，两套凭据冲突启动失败 | 认证及冲突测试 |
| 原 production 开关 | 统一屏蔽新旧 UI、JSON、YAML、分组，不影响业务接口 | MVC 及独立生产模式进程 |
| 原 cors 开关 | 默认不带凭据，带凭据必须明确 Origin，修复非法通配组合 | 默认/白名单/拒绝及独立 CORS 进程 |
| 关闭文档及安全默认值 | 关闭返回 404，不保留隐含弱密码，策略创建时冻结 | 关闭及快照测试 |
| Boot 4 与源码规范 | Springdoc 3；实例级兼容桥；完整注释与 Checkstyle | 全量构建、规范检查 |

## 必要差异

- 上游 4.5.0 增强器仍调用 Springdoc 旧 List 返回签名。兼容桥替换确切来源的默认 Bean，
  保留设置、Markdown 和排序，不通过关闭增强功能避免错误。
- 不改写用户自定义 Bean；调用旧父类实现的自定义代码需使用兼容增强器或 Springdoc 3 接口。
- 不保留默认用户名/密码，也不保留通配 Origin 携带凭据的危险配置。
- 生产模式修复了上游只屏蔽页面、仍允许文档 JSON 访问的问题。
- 不改变业务接口的认证或授权。文档安全属性修改需要重建过滤器。
- 示例明确使用 OpenAPI 3.0 验证原 UI 兼容路径，不声明已覆盖全部 OpenAPI 3.1 扩展。
- 常规 JAR/Boot JAR 部署会读取实际 UI 制品资源清单；非 JAR 资源部署仍保护页面和 API，
  静态公开库资源不包含业务文档数据，不使用整个 webjars 前缀误拦截业务资源。

本轮全量 182 项 Java 测试通过，10 项真实接口及生命周期检查通过，测试进程已退出。
此结果仅属于本模块，不代表其他 Starter 的缺口已关闭。
