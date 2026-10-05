# 脱敏示例

真实依赖脱敏Starter，默认本机18086端口。

- GET `/api/desensitize/profile`：合成手机号、姓名、邮箱、自定义范围及需构造参数的Spring自定义处理器。
- GET `/api/desensitize/list`：嵌套集合序列化。

执行`python3 scripts/test-starter.py desensitize`，自动验证字段规则、普通字符串不变、自定义处理器和嵌套列表。测试数据均为代码中的合成样例，不接入实际个人信息。
