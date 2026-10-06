# 敏感词功能对照

参考同级 `common-tool/sensitive-word-spring-boot-starter` 的 14 个 Java 文件、POM 与内置词库。
本轮先恢复旧版实际提供的能力，再修正索引、白名单、配置与并发问题。2026-10-06 本轮验收通过。

| 原入口 | 迁移要求 | 状态 |
| --- | --- | --- |
| SensitiveWordFilter、DfaSensitiveWordFilter、DfaNode | 原接口经 SensitiveWordActions 继承全部方法；原构造器、分类、增删清空、两种匹配、替换和高亮 | 已实现，LegacyFilterTest |
| SensitiveWordResult、SensitiveWordException | 原 UTF-16 闭区间结果、模型构造器及异常查询方法，异常证据深复制 | 已实现，LegacyFilterTest / SensitiveOperationsTest |
| handler.SensitiveWordService | 继承共享 SensitiveWordOperations，默认策略、文件/资源加载、动态白名单及全部原重载 | 已实现，服务与资源测试、HTTP |
| SensitiveWordCheck、SensitiveWordField、SensitiveWordAspect | 方法/参数/字段策略优先级；修复独立参数注解漏检，保留 around 入口 | 已实现，代理测试与真实 HTTP |
| properties.SensitiveWordProperties | 通过共享 Options 视图保留全部原字段和默认值，与根包配置不漂移 | 已实现，配置共享与绑定测试 |
| SensitiveWordUtil | 原快捷操作保留，静态调用改注入实例，不能建立全局默认过滤器 | 已实现，工具实例隔离测试 |
| 根包现有服务 | 保留 Match 右开区间、原文匹配内容和 Unicode 码点替换，与原包服务共享实际状态 | 已回归全部原测试 |
| 内置资源、Spring 自动配置、示例 | Boot 4 AspectJ、可选 MVC/Jackson 3、用户过滤器/服务覆盖、延迟 Advisor | 单元/示例测试通过 |

## 原缺陷修正

- 白名单原本只影响查询，替换、高亮、AOP 不一致；现在业务入口统一，并正确覆盖重叠白名单。
- 原大小写选项变更不重建树、整串小写扩展导致原文下标错位；现在配置与编译结构一起发布，
  采用单码点折叠并保留原始 UTF-16 映射，匹配不吞词前空白。
- 批量新增和删除只发布完整快照，同批等价词和逐个新增保持首次注册语义，失败保留旧数据。
- 字段改写先计划后应用，后续参数被拒绝不会留下部分修改；final 字段不再尝试不安全反射写入。
- 加载失败不再吞掉，先验证资源/内容再注册；文件名空格、井号、百分号采用 Path/URI 结构化转换。
- 默认 Advisor 延迟获取服务，不通过将业务 Bean 标记为基础设施来掩盖提前实例化问题。
- 原静态 Util 必须改为实例调用，这是消除跨容器全局可变状态的明确迁移变化。
  原包服务 Bean 名为 legacySensitiveWordService，与现有根包 sensitiveWordService 共存。
- 内置过滤器支持原子 replaceWords；自定义过滤器的原增删查询接口全部可覆盖，
  新增的全量快照替换需要使用支持快照的 DfaSensitiveWordFilter，不能偷偷 clear/add 破坏其原子性。

## 原未实现能力

原 Web 配置只有属性、没有过滤器。本轮补充了实际 Servlet MVC 实现：
路径/排除/参数名选择、文本与 JSON 字符串值处理、四种 HandleType、正文和深度上限。
JSON 保留数值精度，改写后长度头与内容一致；拒绝时不输出敏感正文。
非阻塞 Servlet 原始读取、二进制文件内容扫描不在本过滤器范围内，常规 MVC 接口应限定 consumes；
该边界不构成对原已实现能力的删减，具体用法见 README。

## 验证

2026-10-06 实际执行结果：

- 功能对齐：上表旧入口已恢复或提供明确的实例化迁移路径，原来只有属性的 Web 能力已补实现。
- 编码规范：Starter、示例及测试接入 Checkstyle；人工检查资源所有权、白名单一致性、
  DTO 变更原子性和异常信息边界，禁止用法扫描通过。不代表全仓或全部阿里规则已自动验收。
- 单元/接口测试：全量 `mvn --batch-mode --no-transfer-progress clean verify` 通过，
  共 268 项 Java 测试，失败、错误及跳过均为 0；其中本 Starter 20 项、示例 MVC 3 项。
- 真实联调：`python3 scripts/test-starter.py sensitive-word --skip-build` 的 12 项检查通过，
  覆盖词库管理、分类和索引、方法/参数/字段注解、Web 四种处理模式及进程关闭。
  独立模式测试 JVM 和主示例进程均已清理，不依赖外部中间件。
- 工程验证：9 项 Python 测试通过；18 个自有坐标、BOM/示例依赖及普通库 JAR 校验通过；
  `git diff --check` 通过。

报告写入示例 `target/api-test-report.json`，不纳入版本控制。以上范围不包含其余待整改 Starter。
