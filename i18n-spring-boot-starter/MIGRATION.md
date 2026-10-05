# 国际化功能对照

| 原能力 | 当前实现 | 验证 |
| --- | --- | --- |
| I18nProperties 七个属性 | 恢复 Boolean/Integer getter/setter、默认值和模型相等性 | JavaBeans 与默认值测试 |
| I18nService 七种查询重载 | 全部恢复，同时保留 getOrDefault | 全部重载及真实默认文本接口 |
| 当前语言和指定 Locale/语言字符串 | 支持 en_US/en-US，空查询字符串使用当前线程语言 | 单测、HTTP 语言头与空语言 |
| 默认文本和格式化 | 显式默认文本优先，支持 alwaysUseMessageFormat | 默认文本、引号格式化测试 |
| MessageSourceResolvable | 保留多编码、参数和默认文本；空编码数组不越界 | Resolvable 回归 |
| I18nMessageProvider 扩展 | getMessages 与默认 refresh 保留，函数式提供器可用 | 仅实现读取的提供器测试 |
| 内存增改/批量/删除 | 原方法保留，改为整体不可变快照原子发布 | 更新、快照和并发测试 |
| 原内存清空能力 | clear()/clear(Locale) 及管理器对应方法显式提供 | 单语言及整体清空、HTTP |
| 原 ResourceBundleMessageProvider(String) | 保留，并提供缓存时长和类加载器扩展构造 | 文件读取与刷新 |
| 原文件命名 | 标准回退之后加载原精确文件，兼容 ROOT 下划线及脚本标签文件 | 实际文件内容比对 |
| UTF-8 与资源异常 | 显式 Reader 编码、关闭流，错误不被当成缺失消息 | 中文资源及失败 URL 测试 |
| refresh 资源重读 | 原入口保留，清理本实例缓存；0/-1 时长真实生效 | 缓存和刷新测试 |
| CustomMessageSource 三参数构造 | 保留，Spring 消息语义和独立 MessageFormat | 构造、格式及回退测试 |
| CustomLocaleResolver(String) | 原类及接口恢复，支持旧单值下划线头及标准权重协商 | 解析器与真实 HTTP |
| 自动装配、关闭及 Bean 覆盖 | 通过参数注入提供原 Bean，MVC 部分隔离在条件配置中 | 关闭、覆盖及无 Web 类路径测试 |
| 规范 | 全部类/方法/字段注释、无监视器锁、Checkstyle | Maven validate |

接入差异：

- Boolean 原访问器恢复，便捷 is 方法使用包装返回类型，避免破坏 JavaBeans 写入。
- 刷新内存不再删除数据；需要原清空效果时显式调用 clear。
- 读取结果是不可修改快照，使用管理 API 更新，不修改返回 Map。
- 容器工厂方法改为参数注入，Web 解析器使用条件配置；消费方注入注册 Bean，
  原解析器类及消息服务/提供器构造入口保留，不依赖手工调用配置类的工厂方法。
- 无效 provider/语言标签明确报错，不静默截断或回退到错误配置。
- 资源回退遵循标准 Locale 层级，不受机器默认语言影响；原精确文件优先覆盖相同编码。

本轮全量 191 项 Java 测试零失败、零跳过；库及示例 Checkstyle 通过，
7 项真实 HTTP/生命周期检查通过，示例进程已清理。不能据此标记其余 Starter 已完成。
