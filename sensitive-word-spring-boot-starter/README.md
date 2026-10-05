# 敏感词

依赖 `io.github.bytex0:sensitive-word-spring-boot-starter`，注入 `SensitiveWordService`。
配置 `sensitive-word.words`、`white-list`、`dict-paths`（UTF-8 `classpath:` 或 `file:`）。
`enabled=false` 关闭。默认无词库，应用自行维护业务词库，不联网加载。
提供 `findAll(text, longest)`、`contains`、`replace`、`reject`、`replaceWords`、`addWord`、`removeWord`。

原实现审查：静态工具导致上下文串扰，白名单不作用于替换；整串小写转换导致位置偏移，
跳过开头空白扩大命中范围；词库加载错误被吞，AOP 会反射修改调用方实体并记录敏感原文。

迁移使用成熟 Aho-Corasick 引擎，词库更新原子发布、失败保留旧快照，默认 Locale 不影响索引。
处理不记录原文。白名单完整命中范围对所有操作一致；与白名单重叠的敏感命中被排除。
匹配保留原文 UTF-16 索引，**endIndex 改为右开区间**；替换按码点计数，不拆代理对。
长度和词数设硬上限：文本 65536、词长 128 UTF-16 单位、词库 10000 词、资源文件 2 MiB。
读取只保留每个开始位置的候选匹配，避免积累所有重叠结果。

兼容变化：用实例服务替代旧静态 Util、可变 DFA 及反射 AOP；旧参数/字段注解不迁入，
业务在入参边界显式调用 `reject`/`replace`，不隐式修改 DTO。移除旧 HTML 高亮（避免误作 HTML 转义）、
分类字段和未生效的 Web 配置；这不是旧 API 的二进制兼容替换。大小写使用单码点折叠，不做跨码点等价替换。

验证入口：`python3 scripts/test-starter.py sensitive-word`，覆盖真实接口、白名单、空白、Unicode、拒绝和资源上限。
