# 连续迁移批次

## 执行约定

用户本轮明确要求：先提交推送当前阶段成果，补齐余项，然后再完成十个 Starter。
每个 Starter 都必须对照原代码、优化缺陷、补全功能、集成示例、实际运行自动化，
全部通过后详细中文提交并推送 origin/main，直接继续下一项。
阶段快照提交不计入完整 Starter 数量，不用新增模块数量掩盖已有缺口。

## 先完成已有余项

| 模块 | 当前状态 | 剩余内容 |
| --- | --- | --- |
| common-tool | 2026-10-25 功能补齐并通过验收 | 全量 396 项 Java、基础真实检查 20 项；能力映射及兼容边界见模块 MIGRATION.md |
| script | 2026-10-25 剩余功能验收通过 | 全量 405 项 Java、脚本真实检查 13 项、Python 10 项；取消/缓存/配置/Ivy 已验证 |
| dynamic-threadpool | 既有简化实现待补齐 | 注解/工厂/包装器、可调队列、刷新、监控、告警、Actuator、第三方池适配 |

## 后续十个 Starter

以下选择均来自原 common-tool 的实际模块，优先通用安全、数据访问、可靠性及集成能力。
必须先完成已有余项，再按顺序执行，不提前建立只有占位能力的模块。

| 顺序 | 原模块 | 新坐标 artifactId | 状态 |
| --- | --- | --- | --- |
| 1 | signature-spring-boot-starter | signature-spring-boot4-starter | 待原源码逐项审计 |
| 2 | crypto-spring-boot-starter | crypto-spring-boot4-starter | 待原源码逐项审计 |
| 3 | resilience4j-spring-boot-starter | resilience4j-spring-boot4-starter | 待原源码与 Spring 7 兼容核对 |
| 4 | mybatis-plus-spring3-boot-starter | mybatis-plus-spring-boot4-starter | 待原源码与 Boot 4 依赖核对 |
| 5 | robot-message-spring-boot-starter | robot-message-spring-boot4-starter | 待原源码逐项审计 |
| 6 | local-message-spring-boot-starter | local-message-spring-boot4-starter | 待事务、重试和恢复流程审计 |
| 7 | multi-redisson-spring-boot-starter | multi-redisson-spring-boot4-starter | 待与已迁 multi-redis 能力重叠审计，不做空壳包装 |
| 8 | mqtt-spring-boot-starter | mqtt-spring-boot4-starter | 待连接、订阅、重连及真实 Broker 验收 |
| 9 | xxl-job-spring-boot-starter | xxl-job-spring-boot4-starter | 待调度、执行器及真实交互验收 |
| 10 | netty-spring-boot-starter | netty-spring-boot4-starter | 待协议、连接生命周期和真实网络验收 |

## 完成统计

本后续十个批次当前完成数：0/10。
只有功能对齐、编码规范、单元/示例测试和真实联调都通过，且提交推送成功后才更新该计数。
