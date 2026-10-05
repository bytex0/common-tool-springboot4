# Spring Boot 4 坐标

本仓库发布的 Starter 使用 groupId `io.github.bytex0`、版本 `4.0.0-SNAPSHOT`。
artifactId 统一改为 `*-spring-boot4-starter`，不再使用原后缀的发布坐标。
源码目录保留以避免破坏已有路径、历史审查引用和 Maven 目录选择命令。

| 源码目录 | 新 artifactId |
| --- | --- |
| common-tool-spring-boot-starter | common-tool-spring-boot4-starter |
| oss-spring-boot-starter | oss-spring-boot4-starter |
| local-cache-spring-boot-starter | local-cache-spring-boot4-starter |
| docs-spring-boot-starter | docs-spring-boot4-starter |
| excel-spring-boot-starter | excel-spring-boot4-starter |
| i18n-spring-boot-starter | i18n-spring-boot4-starter |
| desensitize-spring-boot-starter | desensitize-spring-boot4-starter |
| dict-spring-boot-starter | dict-spring-boot4-starter |
| multi-redis-spring-boot-starter | multi-redis-spring-boot4-starter |
| lock-spring-boot-starter | lock-spring-boot4-starter |
| rate-limiter-spring-boot-starter | rate-limiter-spring-boot4-starter |
| idempotent-spring-boot-starter | idempotent-spring-boot4-starter |
| ip2region-spring-boot-starter | ip2region-spring-boot4-starter |
| sensitive-word-spring-boot-starter | sensitive-word-spring-boot4-starter |
| disruptor-spring-boot-starter | disruptor-spring-boot4-starter |
| sftp-spring-boot-starter | sftp-spring-boot4-starter |
| script-spring-boot-starter | script-spring-boot4-starter |
| dynamic-threadpool-spring-boot-starter | dynamic-threadpool-spring-boot4-starter |

父工程 `common-tool-springboot4`、BOM `common-tool-springboot4-bom` 已包含版本线标识，保持不变。
示例模块不发布，artifactId 保持不变。Spring 官方和第三方依赖沿用上游真实坐标。
旧坐标不新增转发 POM，消费方需更新依赖；旧本地 Maven 缓存无需删除。

校验：

```bash
python3 -m unittest discover -s scripts -p 'test_check_coordinates.py'
python3 scripts/check-coordinates.py
mvn --batch-mode --no-transfer-progress clean verify
python3 scripts/check-coordinates.py --built-jars
```

自动化接口脚本也会检查坐标和构建制品，避免旧依赖或旧 JAR 掩盖遗漏。

本轮验证：6 项坐标规则测试、173 项 Java 测试、18 个普通库 JAR 检查通过。
基础、本地缓存、IP 归属地和双后端限流示例共 29 项真实检查通过。
这是坐标切换验证，不代表其余 Starter 的功能缺口已经修复。
