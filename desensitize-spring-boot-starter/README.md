# 脱敏 Starter

坐标：`io.github.bytex0:desensitize-spring-boot4-starter`。
提供 Jackson 3、Fastjson 1 API 兼容和 Fastjson 2 原生脱敏路径，保留原独立处理器、
范围处理、策略工厂、模型转换和扩展入口。逐项对照及测试结果见 [MIGRATION.md](MIGRATION.md)。

Java 根包沿用本仓库既有 `io.github.bytex0.desensitize`，旧重复的
`desensitize.desensitize` 层级需调整 import；不是旧二进制制品的直接替换。

## 配置

```yaml
desensitize:
  enabled: true
  enable-jackson: true
  enable-fastjson: false
  enable-mybatis: false
```

总开关沿用本仓库已有的默认 true；原 Boot 3 模型默认 false，建议消费方明确配置。
`enable-jackson` 默认 true，`enable-fastjson` 默认 false，两个开关相互独立。
原 MyBatis 只有属性、没有插件实现；默认工厂遇到 `enable-mybatis=true` 会明确拒绝启动，
不能把无效开关当作已经实现的结果集脱敏。

## Jackson

在 String 字段、getter 或 record 组件上标注 `@Desensitize(type = DesensitizeType.PHONE)`。
只改变出站 JSON，不修改原对象、不处理反序列化输入，不污染未标注属性、Map 值或根字符串。
getter 的显式规则优先于同属性字段，非 String 属性标注会失败。

保留公开 `DesensitizeSerializer(factory)` 和 `DesensitizeModule(module)` 入口；
类型使用 Jackson 3 的 `ValueSerializer`、`JacksonModule`。
每个属性持有不可变规则，不能在共享 serializer 上修改当前字段状态。

## Fastjson

启用 `enable-fastjson=true` 后，按类路径提供：

- `DesensitizeValueFilter`：保留 `com.alibaba.fastjson.serializer.ValueFilter` 接口。
  Maven 使用 `com.alibaba:fastjson:2.0.65`，这是由 Fastjson 2 实现的 1 API 兼容包，不回退到旧 1.x 内核。
- `DesensitizeFastjson2ValueFilter`：支持 `com.alibaba.fastjson2.filter.ValueFilter` 原生接口。
  仅有 Fastjson 2 时可独立使用，不要求同时安装兼容包。

两者均为可选依赖，不使用 Fastjson 时不需要引入。把注入的过滤器传给具体序列化调用或局部配置，
与旧模块一样不会仅因存在 Bean 就替换 MVC 的 JSON 引擎：

```java
String json = JSON.toJSONString(profile, desensitizeValueFilter);
```

示例还演示通过局部 `JSONWriter` 上下文使用原生过滤器。
支持继承字段、getter、record、显式 `JSONField(name=...)`/`JsonProperty` 别名和嵌套对象。
若动态 NameFilter 或其他命名策略产生无法映射的属性名，含脱敏字段的模型会明确报错；
应提供显式别名，不能在无法识别规则时返回原文。
处理器异常、字段类型错误和别名冲突均失败，不保留旧版 catch 后原文回退。
不启用 AutoType，也不修改全局 Fastjson 配置。

## 原规则与修正

| 类型 | 正常输入的默认行为 |
| --- | --- |
| PHONE | 11 位号码保留前三后四位 |
| EMAIL | 保留本地名首码点及域，中间固定四星 |
| NAME | 两字姓名隐藏末字，多字姓名保留首尾 |
| ID_CARD | 保留首末四位，中间按实际隐藏数量替换 |
| BANK_CARD | 保留首末四位，中间固定四星 |
| ADDRESS | 保留前六后两码点，中间固定四星 |
| PASSWORD / MASK_ALL | 固定六星，不暴露长度 |
| CAR_NUMBER | 保留前两码点和末位，中间固定四星 |
| FIXED_PHONE | 保留区号和最后四位 |
| IPV4 | 保留首末段及可选端口，不进行 DNS 查询 |
| IPV6 | 校验字面值后保留首个显式段，后续隐藏 |
| PASSPORT / MILITARY_ID | 保留前后两码点，中间固定四星 |
| CNAPS_CODE | 十二位数字保留首末四位 |
| DOMAIN | 隐藏首段，保留后续域和端口 |

恢复所有原具体处理器类，包括旧拼写 `MastAllDesensitizeHandler`。
原缺失的 IPv6、军官证、联行号处理器现在有明确规则，不再使用缺省原文或无差别简化策略。
原短姓名、短地址、短证件、畸形号码和两段域名可能返回原文，现在安全遮蔽。
这些规则不是法规合规保证，业务需要更严格策略时应使用明确的自定义处理器。

## 范围与扩展

- `AbstractDesensitizeHandler.doDesensitize` 和工厂 `maskRange` 保留范围入口。
  索引按 Unicode 码点计数，起点包含、终点不包含；-1 终点特指整个末尾，修复旧实现遗留末位。
  其他负数从末尾倒数，索引截断后为空或倒置时明确失败。
- 与原抽象处理器一致，非默认索引或单独修改 `maskChar` 都启用范围覆盖；
  默认范围为整串。替换文本为 1 至 8 个码点，不使用空字符串跳过脱敏。
- `CUSTOM` 优先使用指定类型的 Spring Bean，支持构造注入；不存在 Bean 时保留公开无参构造器接入，
  每次创建无状态实例。需要资源或生命周期的处理器必须交给容器，不能依赖此临时构造入口。
- `@DesensitizeFor` 支持代理 Bean，所有单例就绪后注册，避免过早构造循环依赖。
  同一类型多个声明式处理器会失败，动态 `registerHandler` 明确替换一个策略。
- 恢复 `getHandler`、`afterPropertiesSet` 和 `reverse`。默认处理器在构造时即可使用；
  扩展 Bean 的发现延后到单例就绪。`reverse` 默认只返回单元素原输入数组，不是可逆解密。
- 工厂直接调用、Jackson 和 Fastjson 都查询同一实际策略，动态更新不被已缓存的属性规则覆盖。
  自定义处理器须线程安全，不能用 `synchronized`。

## 实例工具

`DesensitizeUtil` 保留 `toJson`、`toObject` 的 Class/TypeReference 重载、
`convertObject` 的 Class/TypeReference 重载，使用 Jackson 3 类型。
静态调用改为构造器注入实例，消除跨上下文共享 mapper。

自动配置工具复制应用的 JsonMapper 配置，独立注册脱敏模块，保留原工具的 NON_NULL 输出约定。
关闭全局 `enable-jackson` 不影响显式工具调用；关闭总开关则不自动提供工具。
`toObject` 与原实现一样只是解析 JSON，不承诺清洗输入；`convertObject` 经过出站脱敏规则，
不修改源对象。解析或策略失败直接抛出异常，不吞掉后返回 null，不记录敏感正文。

## 验证

执行 `python3 scripts/test-starter.py desensitize`，自动构建、启动示例、发起真实 HTTP 请求，
验证三种引擎、record、全部策略、范围错误、并发隔离和进程清理，不依赖外部中间件。
示例接口和运行方式见 [示例说明](../examples-starter/desensitize-example/README.md)。
