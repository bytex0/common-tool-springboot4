# 脱敏功能对照

参考同级 `common-tool/desensitize-spring-boot-starter` 的 27 个主源码文件、POM 和配置。
本轮恢复原成品入口并修复安全边界；最终验收结果记录在文末。

| 原文件及符号 | 目标实现和兼容策略 | 状态 |
| --- | --- | --- |
| annotation.Desensitize / DesensitizeFor | 同名注解保留类型、处理器、范围和替换字符；字段/getter/record 支持 | SerializationCompatibilityTest、示例三引擎 HTTP 通过 |
| enums.DesensitizeType | 同名枚举保留全部 17 项，原缺失 IPv6/军官证/联行号补具体处理器 | HandlerCompatibilityTest、DesensitizeTest、rules HTTP 通过 |
| handler.DesensitizeHandler.desensitize/reverse | 同名两种入口和 reverse 默认恒等数组，不宣称可逆解密 | 工厂、处理器、原无参 CUSTOM 及托管 Bean 测试通过 |
| AbstractDesensitizeHandler.doDesensitize | 同名范围入口，Unicode 码点，-1 真正到末尾；非法范围拒绝 | 单元测试及范围 HTTP 参数/码点断言通过 |
| 全部 13 个原具体 Handler | 同名独立类，包括 MastAll 旧拼写；保留正常输入规则，短值/畸形输入安全修正 | rules HTTP 逐项实际结果通过，完整规则见 README |
| DesensitizeHandlerFactory.getHandler/registerHandler/afterPropertiesSet | 恢复工厂入口；动态注册各路径一致，单例就绪后发现扩展 | HandlerCompatibilityTest 的代理/重复声明及序列化动态替换通过 |
| jackson.DesensitizeModule / DesensitizeSerializer | Jackson 3 包装构造器和上下文序列化器，属性状态不可变 | 包装模块、根字符串、普通字段及多线程测试通过 |
| fastjson.DesensitizeValueFilter.process | 同名 Fastjson 1 API，由 Fastjson 2 兼容实现；增加原生过滤器 | 两种实际 API、继承/别名/record、异常及并发 HTTP 通过 |
| 三个自动配置及 DesensitizeProperties | 恢复总开关、Jackson/Fastjson 子开关；原 MyBatis 属性无实现，明确拒绝伪启用 | DesensitizeConfigurationTest 开关、缺失类路径、用户覆盖通过 |
| util.DesensitizeUtil 五个重载及 ApplicationContextAware 初始化 | 保留 JSON/模型转换；构造器注入与实例 mapper 取代旧静态初始化，不保留全局上下文 | 五个转换入口、NON_NULL、解析失败和 Jackson 子开关隔离测试通过 |

旧包中的重复 `desensitize.desensitize` 层级沿用本仓库既有约定收敛为
`io.github.bytex0.desensitize`。Fastjson 不启用 AutoType，不修改其进程级全局配置。

## 逐项策略

原 Phone/Email/Name/IdCard/BankCard/Address/Password/CarNumber/FixedPhone/Ip/Passport/
MastAll/DomainDesensitizeHandler 均恢复为同名类，其单参方法和继承的注解/范围方法都可直接使用。
补充 Ipv6/MilitaryId/CnapsCodeDesensitizeHandler，不能再以工厂无处理器或简单扩展接口代替。
正常输入保留原首尾长度、固定星号规则和端口；以下属于已记录的缺陷修正：

- 短姓名、短地址、短证件、畸形号码和部分域名原来直接返回原文，现在遮蔽。
- 旧范围 -1 会留下末位，现按注解文档真正到末尾，且不切断 Unicode 代理对。
- 非法范围、非 String 属性、自定义处理器异常、别名冲突或无法映射的动态名称都明确失败。
- Fastjson 原实现只查直接声明字段且吞异常，现解析继承字段/getter/显式别名，不读取私有字段内容。
- 旧 CUSTOM 仅反射构造，现优先使用 Spring Bean，并保留公开无参构造器以兼容无状态处理器。
- 旧工厂按 Bean 遍历顺序覆盖，现拒绝重复声明；代理 Bean 支持目标类注解，避免提前实例化循环。

## 兼容边界

- 原工具静态调用必须改为注入实例，Jackson 类型同步迁到 tools.jackson；不保留静态应用上下文。
- 总开关维持本仓库已有默认 true，与原 Boot 3 模型 false 不同，迁移文档要求显式配置。
  Jackson 子开关默认 true，Fastjson 默认 false，配置对象的默认值实际参与开关判定。
- 保留 `afterPropertiesSet` 入口，默认处理器构造即就绪；扩展发现延至 `afterSingletonsInstantiated`。
- 原 MyBatis 只有一个未使用的属性，不存在结果集插件，默认配置明确拒绝启用。
- Fastjson 使用 2.0.65 的 1 API 兼容包及原生包，不引入旧 1.x 内核；
  过滤器须用于具体调用/局部配置，不假称 Bean 自动替换了 MVC 转换器。
- 掩码是单向展示规则，不可用于恢复原文，也不是特定法规的合规保证。

## 验证

2026-10-06 最终分项验收通过：

- 功能对齐：上表原成品入口和重载已经恢复，原无实现的 MyBatis 属性仍明确标注边界。
- 编码规范：主代码、测试和示例接入 Checkstyle，禁止用法扫描通过；
  人工核对注解默认值、空值、异常、中断、序列化及资源所有权，不宣称全部阿里规则已自动验收。
- 单元/接口测试：最终 `mvn --batch-mode --no-transfer-progress clean verify` 通过，
  共 296 项 Java 测试，失败、错误、跳过均为 0；本 Starter 14 项，示例 MVC 4 项。
- 真实联调：最终脚本完成三种引擎、全部规则、异常保护、Unicode、并发及进程清理 12 项检查。
  联调发现并修复字符串响应误选请求 ID 重载、空 token 被 MVC 默认值替代两项示例问题，
  没有通过放宽业务断言让测试通过。
- 相关回归：Excel 既有能力 6 项真实接口检查通过；不代表其原上下文及异步入口已补齐。
- 工程检查：9 项 Python 测试、18 个自有坐标/BOM/示例依赖及普通库 JAR 校验通过，
  `git diff --check` 通过。

示例进程已清理，不依赖外部服务；报告写入示例 `target/api-test-report.json`，不提交构建产物。
