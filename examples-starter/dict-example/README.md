# 字典示例

真实依赖字典Starter，默认本机18087端口，无需外部服务。

- GET `/api/dict/sample?status=1`，返回重命名后的state/stateText和numeric/numericText。
- PUT `/api/dict/status`，JSON code到text映射，替换测试字典并刷新缓存。
- POST `/api/dict/refresh`，整体刷新。

执行`python3 scripts/test-starter.py dict`，自动验证code保留、文本补充、数值类型、动态替换、刷新和未知code。数据库功能由Starter中H2测试覆盖。
