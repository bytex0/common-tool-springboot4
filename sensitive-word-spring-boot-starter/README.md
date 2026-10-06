# 敏感词

依赖 `io.github.bytex0:sensitive-word-spring-boot4-starter`，注入 `SensitiveWordService`。
配置 `sensitive-word.words`、`white-list`、`dict-paths`（UTF-8 原 classpath 路径、`classpath:` 或 `file:`）。
`enabled=false` 关闭。默认无词库，应用自行维护业务词库，不联网加载。
提供 `findAll(text, longest)`、`contains`、`replace`、`reject`、`replaceWords`、`addWord`、`removeWord`。

原实现审查：静态工具导致上下文串扰，白名单不作用于替换；整串小写转换导致位置偏移，
跳过开头空白扩大命中范围；词库加载错误被吞，AOP 会反射修改调用方实体并记录敏感原文。

迁移使用成熟 Aho-Corasick 引擎，词库更新原子发布、失败保留旧快照，默认 Locale 不影响索引。
处理不记录原文。白名单完整命中范围对业务服务的查询、替换、高亮及拒绝操作一致；
重叠白名单按覆盖范围合并，不通过删除原文改变索引。
词库及匹配选项一起原子发布，批量失败保留旧状态；修改大小写或跳过字符会重新编译原词条。
默认上限：文本 65536、词长 128 UTF-16 单位、词库 10000 词、资源文件 2 MiB，
可通过 `max-text-length/max-word-length/max-words/max-dictionary-bytes` 调整。
读取只保留每个开始位置的候选匹配，避免积累所有重叠结果。

## 原能力恢复

- 原 `core.SensitiveWordFilter/DfaSensitiveWordFilter`、两个构造器及设置方法、`DfaNode` 全部保留。
  底层使用现有 Aho-Corasick 引擎，不要求业务重写匹配逻辑。
- 分类、MIN_MATCH/MAX_MATCH、字符/固定字符串替换、标签高亮、增删清空全部保留。
- 原 `SensitiveWordResult` 返回注册词条和分类，**startIndex/endIndex 都是原文 UTF-16 闭区间**；
  修复了词前空白被误算进区间的问题。
- 根包现有 `SensitiveWordService.Match` 继续返回匹配原文和右开 endIndex，
  `replace(String)` 继续最长匹配并按 Unicode 码点输出星号。两个接口不混用索引协议。
- 原 `handler.SensitiveWordService` Bean 为 `legacySensitiveWordService`，
  根包 Bean 为 `sensitiveWordService`，两者共享词库和白名单。原按 Bean 名注入的使用方需选择对应名称。
- 恢复原 `properties.SensitiveWordProperties` 配置入口，与根包配置共享有效字段和默认值。
- 原异常构造器、getSensitiveWords/getFirstSensitiveWord/getAllSensitiveWords 保留，结果防御性复制。
- 原 `SensitiveWordUtil` 操作保留，但必须改为注入或创建**实例**调用，不能再静态调用；
  `setFilter` 只影响该实例，不引入跨 Spring 容器的全局词库。工具直接访问过滤器，不自动叠加业务白名单。
- 默认不加载词库，原 `sensitive/default.txt` 已打包，配置 `dict-paths: [sensitive/default.txt]`
  或调用 `loadFromClasspath` 可加载。`external-dict-paths/loadFromFile` 只读取本地文件，失败不吞异常。

默认模式仍为 MIN_MATCH、REPLACE、替换字符 `*`；`replaceWithStr/process` 中非空白 `replace-str`
优先于替换字符。`highlight-start-tag/highlight-end-tag` 默认为原 span 标签。
高亮只是包裹原文，不是 HTML 转义或 XSS 防护。大小写采用与 Locale 无关的单码点简单折叠，
不执行跨码点语言等价替换。

## 注解边界

原 `SensitiveWordCheck` 方法/参数注解和 `SensitiveWordField` 字段注解已恢复。
参数可独立触发，不再必须同时标记方法；优先级为字段、参数、方法。
`fields` 只选择对象的直接字符串字段，支持继承字段，不递归进入嵌套对象或集合。
字段策略需在方法或参数 `SensitiveWordCheck` 边界内触发，避免控制器与服务自动重复高亮。

所有参数校验完成后才应用对象字段修改，后续参数拒绝不会留下先前字段的部分修改。
不允许修改 static 字段；REPLACE/HIGHLIGHT 遇到需要改变的 final 字段会明确报错，
不可变 DTO 应使用程序化结果重建。需通过 Spring 代理调用，自调用不触发。
Advisor 只在实际受保护调用时解析服务，避免词库 Bean 提前创建而错过后置处理。

## Web 过滤

原项目仅声明 Web 属性，本版本补上显式启用的 Servlet MVC 过滤器：

```yaml
sensitive-word:
  web:
    enabled: true
    url-patterns: [/api/content/**]
    exclude-patterns: [/api/content/public/**]
    check-params: [text, title]
    check-body: true
    handle-type: REPLACE
    max-body-bytes: 262144
    max-json-depth: 64
```

路径采用 Spring Ant 规则，排除优先；空 includes/check-params 表示全部路径/参数。
查询和表单通过 Servlet 参数 API 处理；请求体只处理 `text/*`、JSON 和 `+json` 的字符串值，
不改写键名、数字、布尔或二进制文件。JSON 浮点采用 BigDecimal，未发生替换时保留原始正文。
正文超限返回 413，非法 JSON、深度超限和 EXCEPTION 策略返回 400。

这不是认证、验签或通用二进制内容检测。生产接口应明确 `consumes`，不能把任意 MIME
当作受保护文本；重要业务边界仍建议使用注解/程序化检查。原始表单和非阻塞 Servlet
读取器不由缓存正文视图重新实现，本视图针对常规 MVC 阻塞绑定，支持 MVC 异步响应。
纯文本使用方无需 Servlet/Jackson；Web 能力依赖应用的 MVC/JSON 栈。

验证：`python3 scripts/test-starter.py sensitive-word`，真实覆盖原/新索引协议、分类和动态白名单、
注解、资源、Web 四种处理策略及限制。完整对照见 [MIGRATION.md](MIGRATION.md)。
