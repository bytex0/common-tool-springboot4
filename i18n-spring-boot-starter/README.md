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

支持UTF-8 properties和memory两种消息来源。资源按标准语言父级回退并限制缓存语言数量，cache-seconds=-1永久、0不缓存；refresh仅清理本提供器缓存。Web使用Spring AcceptHeaderLocaleResolver处理Accept-Language，不再把整个加权头当作语言名称。

通过构造器注入I18nService查询消息，I18nManager维护内存消息。支持en_US和en-US。内存刷新不再删除消息，返回不可变快照；资源提供器不支持动态写入时明确报错，不再静默忽略。

显式默认文本优先于code回退，格式化和MessageSourceResolvable语义交给Spring实现。用`getOrDefault(code, defaultText, locale, args...)`替代旧含糊的String可变参数默认文本重载。默认语言也可作为内存消息缺失语言时的回退。

不再暴露静态消息缓存。用户可覆盖消息提供器、命名messageSource、localeResolver及服务Bean。资源basename是classpath基础名称，不是任意外部URL。注入的类加载器由调用方管理。

验证：`python3 scripts/test-starter.py i18n`。单元测试另外覆盖UTF-8文件、资源回退、TTL=0、刷新、快照及默认文本语义。
