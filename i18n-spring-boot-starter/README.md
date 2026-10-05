# 国际化 Starter

引入 `io.github.bytex0:i18n-spring-boot4-starter`，配置 `i18n.enabled=true`。默认关闭，核心可用于非Web应用。

```yaml
i18n:
  enabled: true
  provider: resource
  basename: i18n/messages
  default-locale: zh_CN
  cache-seconds: 3600
  use-code-as-default-message: true
  always-use-message-format: false
```

支持 UTF-8 properties 和 memory 两种来源。资源按标准语言父级回退并限制缓存语言数量，
cache-seconds=-1 永久、0 不缓存；refresh 仅清理当前提供器的派生缓存。
资源读取显式使用 UTF-8，并兼容原 `basename_<Locale.toString()>.properties` 精确文件名，
包括 ROOT 对应的下划线文件。读取错误不会伪装成缺失翻译。
Web 恢复原 `CustomLocaleResolver` 类，标准权重头委托 Spring 处理，同时保留单值 `zh_CN` 请求头。

通过构造器注入 I18nService 查询，I18nManager 维护内存消息。支持 en_US 和 en-US。
内存返回不可变快照，更新和清空原子发布；使用 `clear()` 清空全部数据，`clear(language)` 清空一种语言。
原来依赖内存 refresh 清空数据的调用需改用 clear，刷新本身不再删除业务数据。
资源提供器不支持写入时明确报错，不静默忽略。

原七种消息查询重载均已恢复，保留 `getOrDefault(code, defaultText, locale, args...)`。
两个字符串参数会选择原来的默认文本重载；纯格式参数用 `new Object[]{value}` 明确传入。
查询中的空语言标签沿用当前线程语言，管理接口的空语言标签表示 Locale.ROOT。
显式默认文本优先于 code 回退，MessageSourceResolvable 和格式化语义由 Spring 实现。

状态按提供器实例隔离。用户可覆盖消息提供器、命名 messageSource、localeResolver 及服务 Bean；
只实现 getMessages 的原提供器仍可使用，refresh 保留接口默认实现。
basename 是 classpath 基础名称，不是外部 URL；类加载器由调用方管理，组件只关闭自己打开的流。
配置恢复原 Boolean/Integer 访问器及模型默认值。

验证：`python3 scripts/test-starter.py i18n`。库和示例已接入 Checkstyle。
完整对照及必要接入差异见 [MIGRATION.md](MIGRATION.md)。
