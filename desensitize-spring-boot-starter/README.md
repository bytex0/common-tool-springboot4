# 脱敏 Starter

使用Jackson 3属性序列化扩展，默认启用，`desensitize.enabled=false`关闭。根包为`io.github.bytex0.desensitize`，移除旧重复的desensitize包层级及Fastjson 1支线。

在String字段或getter上标注`@Desensitize(type=DesensitizeType.PHONE)`。只影响出站JSON，不修改源对象，不处理反序列化输入，不把String全局序列化器带到未标记字段、Map或根字符串。

- 支持手机号、邮箱、姓名、身份证、银行卡、地址、密码、车牌、固话、IPv4、IPv6、护照、军官证、联行号、全遮蔽、域名及自定义类型。
- 原来没有处理器的IPv6、军官证、联行号默认全遮蔽，不再返回原文。
- 短输入和无效邮箱/IP采用保守遮蔽；仅一字符的姓名也遮蔽。
- 范围按Unicode码点计算，endIndex=-1明确包含到末尾。无效空范围报错，不静默泄露原值。
- `maskChar`可定制，长度1至8。掩码不是可逆加密，不应作为原始业务数据存储。
- CUSTOM的handler必须是Spring Bean，允许构造注入；缺少Bean或把注解放到非String属性时失败，不降级为原文。
- 可通过`@DesensitizeFor`的处理器Bean或工厂的registerHandler显式替换内置规则；自定义处理器自行负责线程安全。
- `DesensitizeUtil`改为注入的实例工具，使用应用JsonMapper；不再维护静态映射器，解析异常会传播，不再吞掉错误返回null。

内置策略的可见前后缀属于本组件默认隐私策略，不代表合规结论。涉及特定法规或业务敏感级别时应配置更严格的自定义处理器。不要混用相冲突的属性序列化注解。

验证：`python3 scripts/test-starter.py desensitize`。普通测试还覆盖所有内置类型、Unicode、线程并发、源对象不变、普通文本隔离及错误类型拒绝。
