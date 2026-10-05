# Disruptor 功能对照

逐项阅读同级 `common-tool/disruptor-spring-boot-starter` 的 12 个 Java 文件及 POM。
保留 LMAX Disruptor 作为核心，不使用手写队列替代其序列和等待机制。

| 原文件及符号 | 当前实现与修正 | 验证 |
| --- | --- | --- |
| `template/DisruptorTemplate` 可选指标构造、send | 原相对包路径和 void 签名保留，空指标构造无歧义；满载等待有上限 | 原空构造、SINGLE 多线程发送、真实 void 入口 |
| createQueue 全部参数 | 保留容量、生产者、等待策略、线程工厂、原 MessageHandler；重复名称拒绝 | 七种策略真实 HTTP、多调用线程与内容计数 |
| registerDisruptor/registerMetrics | 保留原生消费链与原指标入口，不伪造外部队列的消费确认 | 原生注册单测及真实 HTTP |
| shutdown/shutdownAll | 单队列和整体清空后可继续创建；新增 close 负责永久停机 | 同名重建、指标替换和自关闭测试 |
| `annotation/DisruptorListener` 全部原属性 | 恢复，线程数和虚拟线程实际生效；新增 inheritDefaults 明确全局配置优先级 | 双线程单次消费、真实线程类型与全局配置测试 |
| `WaitStrategyType` 七项及 create | 全部保留，使用对应 LMAX 实现；修正原时间参数被描述为自旋次数的注释 | 实例测试及全部策略真实收发 |
| `DisruptorProperties` 原四项 | 全部保留并实际接入，新加开关、资源与等待上限 | 属性生效、非法容量、关闭开关 |
| `DisruptorEvent/DisruptorEventFactory` | 原 data 访问和新建入口保留，托管消费后释放引用，内部确认不参与原相等判断 | 模型工厂与消费测试 |
| `handler/MessageHandler` 与 Adapter | 原函数接口保持 Event 参数，适配器不篡改外部处理链 | 动态工厂及原生 LMAX 消费 |
| `DisruptorHandler` | 原扩展类型保留，默认只记录序号，不输出消息正文 | 类型编译与源码复核 |
| `DisruptorListenerProcessor` 原构造与 BPP | 单例完成后注册，保留代理调用、解析真实业务异常，重复定义不泄漏旧线程 | Spring 代理、受检异常、监听 HTTP |
| `DisruptorMetrics` 原构造及注册方法 | 原 Gauge 名称/标签保留，同名替换和组件关闭移除自己拥有的指标 | 指标重建、手工登记关闭测试 |
| `config/DisruptorAutoConfiguration` 原工厂 | 原类型与工厂方法保留，原模板/新模板共享引擎；静态 BPP 工厂使用延迟提供器 | 实际示例通过两条 imports 自动装配 |
| 既有根包 MessageHandler/DisruptorTemplate | 原类型化 API 保留，消费确认、类型校验、满载拒绝和 SmartLifecycle 继续有效 | 原三项回归及并发关闭确认测试 |

## 并发与边界

- 发布锁只执行有限内存操作，关闭先禁止发布，再在锁外等待；不使用 synchronized。
- 多工作线程固定按序号分配，不重复广播，不保证多线程完成顺序。默认单线程保持顺序。
- SINGLE 发布通过显式互斥串行化，支持多个调用线程，不假冒为 MULTI。
- 消费者自关闭不等待当前线程；并发发布/关闭后，所有已返回的确认都有完成或失败状态。
- 原生外部队列的任意消费者无法由包装层确认完成，因此只保留原入队语义；
  外部线程工厂必须自行配合终止，不谎报业务确认。
- 原 void send 不再无限等待，默认最多 5 秒，配置范围见 README；队列容量统一校验为
  2 至 1048576 的二次幂，线程数为 1 至 128，注册数默认最多 64。
- 指标按实际实例注销，旧队列迟到关闭不能移除新环指标，组件退出也移除手工登记项。
- 原包模板 Bean 名改为 legacyDisruptorTemplate，以便与既有根包模板共存；按类型注入。
- 超时、强制停止或未来失败不等同于撤销业务副作用，组件不是持久化/事务消息队列。

## 验收记录

2026-10-06 验证通过：

- 全量 `mvn --batch-mode --no-transfer-progress clean verify`：250 项 Java 测试通过，无失败/跳过；
  Disruptor Starter 12 项，示例 MVC 3 项。
- `python3 scripts/test-starter.py disruptor --skip-build`：10 项真实检查通过，覆盖全部七种等待策略、
  SINGLE 多发布线程、注解并行一次消费、平台/虚拟线程、满载 429、原 void send、
  原生注册不伪造确认、同名重建及指标删除。
- 本批十个 Starter 使用最终构建逐一回归，104 项真实接口及生命周期检查全部通过。
- Python 坐标/测试服务构建器 9 项测试通过，18 个自有库制品及 BOM/示例坐标检查通过。
- Checkstyle 覆盖主代码、测试和示例；人工复核原接口、字段/枚举/方法注释、消息引用释放、
  发布关闭竞争及原生队列确认边界。没有 synchronized、Autowired 或通配符导入。
- 本模块示例进程、动态队列和指标已清理；本批其他测试进程与专用服务也全部清理。

本批功能对齐、规范与验证流程完成。其余未纳入批次的 Starter 仍不能据此认定完整迁移。
