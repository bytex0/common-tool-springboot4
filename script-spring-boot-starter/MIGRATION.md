# Script 功能迁移对照

## 当前状态

本次对照依据同级 `common-tool/script-spring-boot-starter` 实际源码、POM 和当前模块。
已新增五语言执行器、类型化服务、有界租约缓存、配置对象及对应示例，
**尚未完成全部兼容边界验收，不能标记完整迁移**。
以下“待补齐”不代表删除授权；未完成实现和实际验证前不得标记迁移完成。

原文件均相对于原模块的 `src/main/java/io/github/archer099/script/`。
目标根包为 `io.github.bytex0.script`。

## 初始缺口对照

以下表格保留实现前的审计快照，当前实现进展见后文。

| 原文件及符号 | 当前实现位置 | 兼容策略及待补齐内容 | 验收要求 | 状态 |
| --- | --- | --- | --- | --- |
| `executor/ScriptExecutor#getType` | `ScriptExecutor#language` | 保留字符串语言入口，恢复类型枚举及旧执行器契约 | 所有语言类型映射、未知类型拒绝 | 待补齐 |
| `executor/ScriptExecutor#execute/executeMethod` | `ScriptExecutor#execute` | 保留脚本体入口，补指定方法执行；不得返回空值掩盖不支持 | 默认调用、指定方法、方法不存在、参数转换 | 待补齐 |
| `executor/ScriptExecutor#compile/executeCompiled/executeCompiledMethod/validate` | 无 | 恢复编译、复用与语法校验；明确编译产物资源所有权 | 编译错误、错误类型产物、复用隔离、关闭行为 | 待补齐 |
| `executor/GroovyScriptExecutor` 全部公开方法 | `GroovyScriptExecutor#execute` | 当前脚本体 `run()` 必须保留；旧入口默认调用 `execute(Map)`，两者不能混同 | 两种默认语义、指定方法、独立 Binding、循环取消 | 部分实现 |
| `executor/JavaScriptExecutor` 全部公开方法 | 无 | 恢复 GraalJS 执行及编译；补齐原来返回 null 的方法入口；不共享 Context | 表达式、方法、语法错误、状态隔离、结果在 Context 关闭后仍可使用 | 待补齐 |
| `executor/LuaScriptExecutor` 全部公开方法 | 无 | 恢复 Lua 执行及编译，保留原结果字符串约定；补指定方法，避免共享 Globals | 原参数转换、字符串返回、方法、沙箱配置、并发隔离 | 待补齐 |
| `executor/PythonScriptExecutor` 全部公开方法 | 无 | 恢复 Jython 兼容能力；纠正对代码对象调用 `__call__()`；不宣称支持 Python 3 | Python 2.7 语法、编译执行、指定方法、独立解释器及关闭 | 待补齐 |
| `executor/JavaExecutor` 全部公开方法 | 无 | 恢复 JDK 编译及 `execute(Map)`；指定方法不能继续返回 null | 包声明、类名解析、同名不同源码隔离、编译失败清理、无 JDK 编译器 | 待补齐 |
| `service/ScriptService#execute/executeMethod` | `ScriptService#execute` | 保留现有无 ID 入口，补 ID 与类型入口；编译与排队纳入超时 | 同 ID 换源码、换类型、并发执行及排队超时 | 待补齐 |
| `service/ScriptService#refresh/remove/validate` | 无 | 恢复显式刷新、删除、校验；定义刷新失败及与执行竞争的行为 | 刷新成功与失败、删除后重编译、校验无业务副作用 | 待补齐 |
| `service/ScriptService#run/addExecutor/getExecutor/getSupportedTypes` | 构造器初始化不可变注册表 | 保留初始化与动态注册能力；不沿用静态跨上下文状态 | Bean 覆盖、替换执行器后不复用旧产物、只读类型快照 | 待补齐 |
| `cache/ScriptCache#get/put/remove/clear` | 无 | 恢复缓存管理能力，改为应用实例拥有的有界缓存；静态全局 API 变化需记录 | 容量、淘汰、清空、跨上下文隔离、并发刷新及删除 | 待补齐 |
| `cache/ScriptCache.CachedScript` 的 md5、scriptContent、compiledScript、type、lastUpdateTime | 无 | 保留元数据能力；内容摘要不是安全验证，不能只比较 ID | 内容和类型一致性、时间更新、元数据不可变性 | 待补齐 |
| `enums/ScriptType` 全部枚举项 | 字符串 `groovy` | 恢复 GROOVY、JAVASCRIPT、LUA、PYTHON、JAVA；枚举逐项 Javadoc | 五类自动装配及独立关闭 | 待补齐 |
| `exception/ScriptCompileException/ScriptExecuteException/ScriptValidateException` 两个构造器 | 原异常直接传播 | 恢复 message 与 message/cause 构造器；保留原始原因及中断语义 | 编译、执行、校验异常分类及 cause | 待补齐 |
| `config/ScriptProperties#enabled/cacheSize/timeout` | `ScriptAutoConfiguration` 的 Value 参数 | 当前显式启用策略保留并记录与旧版默认启用的区别；缓存容量必须生效 | 关闭无执行器、容量边界、超时边界 | 部分实现 |
| `config/ScriptProperties.GroovyProperties#enabled/cacheSize` | 无 | 语言开关及编译缓存容量实际生效 | 关闭、配置绑定、容量限制 | 待补齐 |
| `config/ScriptProperties.JavaScriptProperties#enabled/strictMode` | 无 | 语言开关和严格模式实际生效，不能仅声明字段 | 开关及严格模式差异用例 | 待补齐 |
| `config/ScriptProperties.LuaProperties#enabled/sandbox` | 无 | 开关及受限标准库配置实际生效；不把进程内执行声称为安全沙箱 | 文件和系统能力限制、开启关闭行为 | 待补齐 |
| `config/ScriptAutoConfiguration` 的五语言 Bean 与服务 Bean | `ScriptAutoConfiguration` 的 Groovy 和服务 Bean | 按语言类路径、开关及用户 Bean 条件装配；可选引擎不强制消费方引入 | 缺少可选依赖仍能启动、默认关闭、用户覆盖、多上下文 | 待补齐 |
| 原 POM Groovy、Ivy、GraalJS、LuaJ、Jython 依赖 | 当前仅 Groovy | 核对 Java 21 与 Boot 4 兼容性，版本集中管理；动态拉取依赖能力需显式安全边界 | 依赖树、普通库 JAR、实际引擎执行 | 待补齐 |

## 必须修复的原实现问题

- 缓存只按 ID 命中，忽略内容、类型和执行器变化；配置容量未实际约束静态缓存。
- 原服务的静态 HashMap 及原引擎的共享执行上下文存在并发和跨应用污染风险。
- 刷新、淘汰、关闭不能提前释放仍在执行的编译产物；不得持锁执行脚本。
- Java 使用固定临时目录和文本拆分提取类名，可能出现同名覆盖，文件管理器未关闭。
- JavaScript、Lua、Python、Java 的指定方法入口原来未实现，需补实现而非删除。
- Python 原代码将编译结果当作函数调用，必须用真实解释器执行测试验证修复。
- 原语言开关、严格模式及 Lua 沙箱配置没有完整接入，不能继续作为无效配置保留。
- 超时只能保证调用等待边界；任意受信 Java 回调不一定响应中断，不能宣称强制终止。

## 验证计划

保留 `/api/script/run?name=sum|product|timeout&a=...&b=...`，示例不允许客户端提交源码。
新增接口只能选择服务端固定脚本，覆盖五语言、方法调用、缓存刷新删除和校验。
现有 `ScriptTest` 与 `ScriptExampleTest` 不能证明上述缺失功能已经通过。

实现后必须执行：

```bash
mvn --batch-mode --no-transfer-progress clean verify
python3 scripts/test-starter.py script --skip-build
python3 scripts/check-coordinates.py --built-jars
git diff --check
```

初始对照阶段未新增上述能力，后续实现及验证结果记录如下。

## 当前实现与测试映射

目标文件均位于本模块 `src/main/java/io/github/bytex0/script/`。

| 原能力 | 当前实现 | 已新增回归测试 | 验收状态 |
| --- | --- | --- | --- |
| 类型枚举及三类异常的两种构造器 | `enums/ScriptType`、`exception/*Exception` | `CompiledExecutorTest` 的异常分类与原因保留 | 已实现，需结合整体迁移验收 |
| 完整执行器契约 | `executor/ScriptExecutor` 的执行、方法、编译、编译后执行、校验及 release | `CompiledExecutorTest` | 已实现 |
| Groovy 默认方法和指定方法 | `executor/GroovyScriptExecutor`，每次独立 Script/Binding | `groovyPreservesMethodAndBodySemantics`、`groovyCompiledBindingsAreIsolated` | 已实现 |
| 原有 Boot 4 Groovy 脚本体入口 | 根包四类继续保留 | `ScriptTest`、`ScriptExampleTest#runsOnlyKnownScripts` | 保留 |
| Java 动态编译及执行 | `executor/JavaExecutor`，语法树解析类名、独立目录和执行加载器 | `javaCompilesPackagedClassAndInvokesMethods`、`javaSameNameRevisionsRemainIndependent` | 已实现 |
| JavaScript | `executor/JavaScriptExecutor`，独立 Context、指定方法、数据结果转换 | `PolyglotExecutorTest` 中 JavaScript 两项测试 | 已实现 |
| Lua | `executor/LuaScriptExecutor`，独立 Globals、方法调用及指令中断检查 | `luaPreservesStringResultsAndSupportsMethods` | 已实现 |
| Python | `executor/PythonScriptExecutor`，独立解释器，使用 exec 执行代码对象 | `pythonExecutesCodeInsteadOfCallingCodeObjects` | 已实现 |
| 按 ID 执行、指定方法、刷新、删除、校验和动态注册 | `service/ScriptService` | `typedServiceSupportsCacheManagementAndRegistration` | 已实现 |
| 缓存 get/put/remove/clear 及元数据 | `cache/ScriptCache`，put 接收所属执行器，执行通过租约获得资源 | `ScriptCacheTest` | 已实现安全替代入口，源码兼容变化见下文 |
| 容量、源码变化、关闭及失效竞争 | `ScriptCache` 的容量限制、revision、引用计数及关闭 | `ScriptCacheTest` 的淘汰、关闭、删除竞争及语言配额测试 | 已实现 |
| 原配置对象及语言开关 | `config/ScriptProperties`、`config/ScriptAutoConfiguration` | `ScriptAutoConfigurationTest` | 已实现；仍需完整核对旧配置命名组合 |
| 普通调用和真实 Starter 集成 | `TypedScriptController` 的 run/cache/validate | `ScriptExampleTest`、`scripts/test-starter.py script` | 实际打包 HTTP 验证 11 项通过 |

## 本轮实际验证

- `python3 scripts/test-starter.py script` 完成全量 clean verify：334 项 Java 测试，
  无失败、错误或跳过；18 个 Starter 坐标、BOM 与普通库 JAR 检查通过。
- 真实 HTTP 共 11 项检查通过：既有脚本体运算、并发隔离、Groovy 超时恢复，
  五语言指定方法、缓存源码变化与刷新删除、语法校验、未知类型拒绝以及启动停止。
- 首次真实打包运行暴露 Jython 无法加载 site 标准库。已通过应用类加载器及
  `__pyclasspath__/Lib` 修复，Python 示例使用 json 标准库验证，未通过禁用 site 绕过问题。
- `python3 -m unittest discover -s scripts -p 'test_*.py'`：9 项通过。
- Script 及示例已接入 Checkstyle，包含测试；本次涉及 Java 源码禁止用法扫描未发现
  synchronized、@Synchronized、@Autowired 或通配符 import。静态检查不是全部阿里规范验收。
- 自动化 Jython 缓存使用测试临时目录；已清理此前调试创建的仓库目录缓存。
- 以上通过范围不覆盖下方剩余验收事项，不标记完整迁移；按用户本次明确要求提交并推送阶段成果。

## 明确的兼容变化

- 原静态全局服务注册表和缓存不再保留跨应用共享状态。服务在构造时完成注册，保留 `run` 启动入口。
- 缓存改为应用实例拥有，`put` 需要传入创建执行器而非仅传类型，以便确定资源释放策略。
  已缓存产物通过 `acquire` 获得租约，不能从元数据快照取出资源后任意关闭。
  元数据改为不可变对象，不再通过 setter 修改缓存内部状态。
- Groovy 原默认 `execute(Map)` 与 Boot 4 已有根包 `run()` 都保留，不能相互替代。
- Java 使用 JDK 21，默认方法和指定方法都要求接收 Map。校验由真实编译判断语法，
  不再使用旧版的签名文本字符串匹配；是否存在指定入口在执行时检查。
- JavaScript 默认关闭宿主访问，显式设置 `script.java-script.allow-host-access=true` 可恢复原版互操作能力。
  普通结果转换为 Java 数据，避免返回依赖已关闭 Context 的 Value。
- Lua 保留字符串参数转换及字符串结果；默认移除系统、文件和 Java 互操作入口，
  `script.lua.sandbox=false` 可恢复完整标准库，不代表脚本已被安全隔离。
- Python 仍是 Python 2.7。原代码对象执行路径失效，本版通过 exec 执行，
  脚本体读取 `result` 变量，指定方法返回其调用结果。
- 编译期间 AST 转换、依赖解析或脚本语言扩展可能有副作用，校验不构成安全扫描。
- 超时保证调用方等待有界，不保证强制终止 Java、JavaScript、Python 或不响应中断的第三方回调。
  已中断的编译任务不发布新缓存；忽略或清除中断的受信代码仍需业务自行控制副作用。

## 剩余验收

缓存公开 API 迁移方式、全部旧配置命名组合、更多关闭/取消竞争和 Ivy 动态依赖解析
仍需继续核对验证。上述内容未全部验收前，不将本模块标记为完整迁移；本次提交仅保存阶段成果。
