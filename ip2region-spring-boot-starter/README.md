# IP 归属地

依赖 `io.github.bytex0:ip2region-spring-boot4-starter`。使用 ip2region 2.7 的 IPv4 XDB 引擎。
显式配置 `ip2region.enabled=true` 和 `ip2region.db-path`（文件路径或 `classpath:`）后注入 `Ip2RegionTemplate` 调用 `search(ip)`。
库不捆绑数据、不联网更新；生产数据库由应用维护。默认文件上限 64 MiB，可配置 `max-database-bytes`，硬上限 256 MiB。

迁移审查：原代码只支持文件、错误返回 null、解析固定索引、Bean 无法覆盖。
现在支持可执行 JAR 内资源、关闭输入流和搜索器、串行保护搜索器状态、严格 IPv4 输入、保留空字段；
未知记录仍返回 null，配置和数据库错误明确抛出。`RegionResult` 恢复原 JavaBean 构造、getter/setter、
equals/hashCode，同时保留此前版本 country()/city() 等读取入口；不再依赖 record 反射。
搜索使用显式锁并沿用引擎的十进制 IPv4 解析，允许前导零但不会执行 DNS 查询。
启动时校验 XDB 版本和索引边界，避免足够大的垃圾文件被误接受。
支持自定义模板和搜索器，不强制消费方引入 Web、Hutool 或 AOP。
IPv6 不在 2.7 引擎支持范围，明确拒绝，不进行 DNS 解析。

验证：`mvn --batch-mode --no-transfer-progress clean verify`；
`python3 scripts/test-starter.py ip2region --skip-build`，包含真实 XDB、并发查询及非法输入。

完整对照与本轮验证见 [迁移记录](MIGRATION.md)。库和示例已接入包含测试代码的 Checkstyle。
