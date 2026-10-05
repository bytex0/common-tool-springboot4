# 字典 Starter

基于Jackson 3为`@Dict("status")`标记的标量属性追加`statusText`等文本字段，保留原始code。默认启用，`dict.enabled=false`关闭。默认内存加载器无需数据库。

- 原静态`DictCache`改为注入实例，缓存为不可变快照，整体刷新原子替换，失败保留旧数据。
- 默认最多1024个字典类型，可用`dict.max-types`调整。缺失类型负缓存到下次刷新，不反复访问后端。
- `dict.auto-refresh=true`在单例初始化后加载全部字典，false则按需加载。
- 支持自定义DictLoader、DictCache和DictModule Bean。InMemoryDictLoader提供原子replace。
- 追加字段使用真实JSON属性名，支持@JsonProperty重命名；原属性因包含策略被省略时不会单独输出文本。
- 文本字段冲突会报错，不输出重复JSON键。未找到code只保留原值，不编造label。
- 普通JSON对象及对象列表受支持，不为Bean-as-array形态添加无名元素；动态字段不会自动成为OpenAPI DTO定义。

数据库支持通过显式注册`JdbcDictLoader`使用，应用自行引入spring-jdbc并管理数据源。TableMapping限定简单表名、code列、text列和可选type列，值使用PreparedStatement绑定，单种字典最多100000行，重复code或null数据报错。

移除原注解table/field直接拼SQL和缺失绑定参数的实现。旧静态调用及table/field注解需要改为注入缓存、配置加载器。字典默认是应用级共享数据，多租户应使用独立类型键或自定义租户隔离实现，不能把依赖当前用户的加载器接入同一个全局type。

验证：`python3 scripts/test-starter.py dict`。单元测试覆盖内存快照、失败保留、字段冲突、省略规则和真实H2 SQL参数安全。
