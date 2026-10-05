# 字典 Starter

坐标：`io.github.bytex0:dict-spring-boot4-starter`。

基于Jackson 3为`@Dict("status")`标记的标量属性追加`statusText`等文本字段，保留原始code。默认启用，`dict.enabled=false`关闭。默认内存加载器无需数据库。

- 原静态 DictCache 改为注入实例，保留 `init/refresh/refreshAll` 和四参数查询能力。
  数据来源和缓存一起原子发布，不持锁调用加载器；慢加载遇到并发刷新会丢弃旧结果。
- 默认最多 1024 个字典类型，可用 `dict.max-types` 调整。未知类型负缓存到下次刷新，
  不反复调用 DictLoader；显式数据库回退仍按值查询，批量场景建议使用下述 JdbcDictLoader。
- 恢复 DictProperties、DictRefresher 及原属性构造方式。`dict.auto-refresh=true` 在单例初始化后
  加载全部字典，false 则按需加载；Runner 不重复预热。属性 enabled 默认 true，
  与旧自动配置 matchIfMissing 的实际行为一致，修复旧属性字段为 false 的自相矛盾。
- 支持自定义DictLoader、DictCache和DictModule Bean。InMemoryDictLoader提供原子replace。
- 追加字段使用真实JSON属性名，支持@JsonProperty重命名；原属性因包含策略被省略时不会单独输出文本。
- 文本字段冲突会报错，不输出重复JSON键。未找到code只保留原值，不编造label。
- 普通JSON对象及对象列表受支持，不为Bean-as-array形态添加无名元素；动态字段不会自动成为OpenAPI DTO定义。

## 数据库字典

恢复原注解 `table/field` 和 `getDictText(type,value,table,field)`，严格验证表列标识符，
值通过 `?` 绑定。原协议以 field 同时作为匹配列和显示列；新增 codeField 支持编码与文本分列：

```java
/**
 * 部门编码，显示文本由可信表字段映射补充。
 */
@Dict(value = "department", table = "dict_departments", field = "label", codeField = "code")
private String department;
```

只有缓存缺少对应编码且显式配置了 table/field 时才访问 JDBC，不创建数据源或连接配置。
表列名必须是业务源码/可信配置指定的简单 SQL 标识符，不能来自用户输入；
找不到值返回 null，重复结果、SQL 错误和缺失 JdbcTemplate 明确报错。

原 JdbcDictLoader 预加载能力继续保留。TableMapping 限定简单表名、编码列、文本列和可选类型列，
单种字典最多 100000 行，重复编码或 null 数据报错，参数绑定失败也由 JdbcTemplate 清理资源。
JDBC 类型是公开 API 的一部分，因此提供 spring-jdbc 库依赖，但不附带数据源 Starter 或数据库驱动。

## 序列化与兼容

- 默认 DictModule 不替换全局 String 序列化器，支持数值属性、实际 JSON 名称和包含策略。
- 恢复 DictSerializer 无参构造和上下文入口。单独使用推荐 `new DictSerializer(cache)`；
  无参实例由 DictModule 在当前序列化作用域提供缓存，或使用 ObjectWriter 的
  `withAttribute(DictCache.class, cache)`。无归属缓存时明确报错，不使用跨应用静态兜底。
- 原包装构造 `new DictModule(module)` 保留，参数由 Jackson 2 Module 改为 Jackson 3 JacksonModule。
  显式委托模块仍由调用方配置，内置缓存模块与旧字符串序列化器共存时不会重复追加文本。
- 原 `DictCache.init(...)` 静态调用需要改为 `cache.init(...)`，同理查询和刷新使用注入实例。
  这是去掉静态跨容器共享状态的明确迁移变化，不是二进制兼容替换。
- 字典默认按应用共享，多租户必须在类型维度隔离；不要让同一个类型加载器依赖当前请求用户。

验证：`python3 scripts/test-starter.py dict`。示例使用独立 H2 数据库执行真实表字段 HTTP 测试；
完整功能映射见 [MIGRATION.md](MIGRATION.md)。
