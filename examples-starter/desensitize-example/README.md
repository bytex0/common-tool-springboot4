# 脱敏示例

真实依赖 `desensitize-spring-boot4-starter`，默认仅绑定本机 18086 端口。
示例明确开启 Jackson、Fastjson 兼容 API 和原生 Fastjson 2，过滤器来自 Starter 自动配置。

- GET `/api/desensitize/profile`：合成手机号、姓名、邮箱、自定义范围及需构造参数的Spring自定义处理器。
- GET `/api/desensitize/list`：嵌套集合序列化。
- GET `/api/desensitize/engines/{engine}`：继承模型、别名和多字姓名，engine 为 jackson/fastjson/fastjson2。
- GET `/api/desensitize/records/{engine}`：record 组件注解，输出手机号仍遮蔽。
- GET `/api/desensitize/failure/{engine}`：错误范围导致 400，响应不能包含原文字段。
- GET `/api/desensitize/converted`：显式工具转换后的 Map。
- GET `/api/desensitize/rules`：全部 16 项非 CUSTOM 策略的固定合成输入输出；CUSTOM 由 profile 覆盖。
- GET `/api/desensitize/range?value=ABCDE&start=1&end=-1&token=%23`：返回 `data.value`，
  超长输入、空替换字符和倒置范围返回 400。

执行 `python3 scripts/test-starter.py desensitize`，自动构建、启动、等待健康就绪，
核对全部规则、三种引擎、record、错误保护、Unicode 范围及并发隔离，最后停止应用。
当前代码已经构建后才使用 `--skip-build`。报告写入 `target/api-test-report.json`。

测试数据均为合成样例，不接入实际个人信息。普通 MVC 测试真实加载 Starter，
本模块不需要外部中间件。Fastjson 过滤器不修改全局状态，也不会替换应用默认的 Jackson MVC 转换器。
