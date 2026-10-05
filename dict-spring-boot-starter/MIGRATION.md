# 字典功能对照

参考同级 `common-tool/dict-spring-boot-starter` 的 8 个 Java 文件、POM 和自动配置资源。

| 原文件及符号 | 当前能力与兼容变化 | 验证 |
| --- | --- | --- |
| `DictCache.init/getDictText/refresh/refreshAll` | 原业务操作全部保留；静态调用改为注入实例，增加安全分列查询 | 初始化隔离、失败保留、四参数查询单测 |
| `DictLoader.loadDict/loadAllDict` | 原方法签名保留，数据复制后发布；null 单类型视为未知，null 全量快照视为加载错误 | 缓存与刷新测试 |
| `DictRefresher(DictProperties)`、run | 原构造可由 BeanFactoryAware 绑定当前缓存，单例和 Runner 不重复加载 | 原构造及启动回调测试 |
| `DictProperties.enabled/autoRefresh` | 恢复配置类，默认 enabled 与旧自动配置实际启用行为统一；添加容量配置 | 属性绑定、关闭刷新测试 |
| `annotation/Dict.value/suffix/table/field` | 属性全部保留，新增 codeField 修复原编码文本同列限制；方法注解也支持 | 字符串/数值、真实 JDBC HTTP |
| `DictSerializer()`、serialize、createContextual | 适配 Jackson 3，缓存按实例或当前序列化上下文提供；不污染普通字符串 | 包装模块、全局序列化器、重复字段测试 |
| `DictModule(Module)`、setupModule/name/version | 恢复委托构造，类型升级为 JacksonModule；默认走属性增强 | 原包装构造单测、真实 Jackson 输出 |
| `DictAutoConfiguration` 两个原 Bean 工厂入口 | 原参数重载保留，新增默认内存来源、可覆盖 Bean 和可选 JDBC | 自动配置、用户来源、启动关闭测试 |
| 原 table/field 数据库兜底 | 修复 queryForObject 漏传参数，标识符校验，绑定编码，零结果与重复结果区分 | H2 单测与真实 HTTP 注入形状输入 |
| 已有 JdbcDictLoader | 保留类型映射和完整加载，PreparedStatementSetter 由 Spring 管理，失败也清理 | 类型值绑定和恶意标识符测试 |

## 优化与边界

- 配置、加载器和数据作为一个不可变版本发布；查询不持锁执行加载器，慢加载不会覆盖已刷新的内容。
- 初始化/刷新失败或类型容量超限保留原数据；不使用 synchronized 或静态共享容器。
- 原静态调用必须改用实例，新的表字段协议保留原同列查询并允许显式不同编码列。
- SQL 表列名属于可信代码配置，简单标识符校验不是数据库授权机制；不向外部请求暴露任意表列选择。
- 不默认缓存单值 JDBC 回退，避免在无过期策略时引入无限陈旧数据；批量查询可通过 JdbcDictLoader 预热。
- 多租户数据必须使用隔离类型或独立实例。数组形态 Bean 和动态字段 OpenAPI 推导不由此模块新增支持。
- 默认属性增强与原字符串序列化器可共存，不重复写入文本；纯字符串根值和普通 Map 不翻译。

## 本轮验收

2026-10-06 验证通过：

- 全量 `mvn --batch-mode --no-transfer-progress clean verify`：229 项 Java 测试通过，无失败/跳过；
  字典 Starter 11 项，示例 MVC 3 项。
- `python3 scripts/test-starter.py dict --skip-build`：7 项真实检查通过，包含实际 H2 表字段翻译、
  原四参数查询、来源与缓存隔离、刷新、绑定参数和非法更新后保留原数据。
- 示例进程正常关闭，H2 连接池随容器关闭；18 个自有库制品及 BOM/示例坐标检查通过。
- Checkstyle 覆盖本模块主代码、测试、示例；人工核对原能力、注释含义、资源关闭、
  并发刷新与 SQL 信任边界。无 synchronized、Autowired 或通配符导入。

原业务能力及本轮功能、规范、测试验收通过。静态调用改实例、Jackson 3 类型变化和 SQL 协议
兼容方式已在 README 明确说明，不是旧二进制的直接替换。
