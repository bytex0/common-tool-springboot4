# IP 归属地功能对照

| 原能力 | 当前实现与改进 | 验证 |
| --- | --- | --- |
| Ip2RegionProperties 的 enabled/dbPath | 保留原 Boolean getter/setter 和模型默认 true；自动配置仍需显式开启 | 配置关闭、覆盖和 JavaBeans 测试 |
| 配置类 searcher(properties) | 保留程序化调用，增加资源加载器重载、大小及索引边界校验 | 真实 XDB 示例、损坏文件测试 |
| ip2RegionTemplate(Searcher) | 保留，支持用户 Bean 覆盖，不擅自关闭借用搜索器 | 自动配置覆盖测试 |
| search(String) | 保留 IPv4 查询与原十进制前导零解释；错误明确抛出，非 DNS 查询 | 真实地址、非法地址、异常恢复 |
| RegionResult.fromRawString | 保留五字段映射及未知值，修复尾部空字段解析 | 字段解析测试 |
| 原 DTO 无参构造/getter/setter/相等性 | 全部恢复，同时提供五个 record 风格读取别名 | 模型契约及 JSON 接口 |
| 搜索器可变状态保护 | ReentrantLock 替代监视器锁，异常后其他线程仍可查询 | 跨线程异常恢复、并发真实查询 |
| 资源与规范 | 流及时关闭，搜索器由容器关闭，主代码和示例接入 Checkstyle | 全量构建及进程退出 |

差异：损坏数据不再吞异常返回 null；数据文件限制最多 256 MiB；使用可变 JavaBean 恢复原契约，
依赖旧 Boot 4 初稿 record 反射的代码需调整。未迁入原引擎本来就不支持的 IPv6，不将其计作功能丢失。

本轮全量 171 项 Java 测试通过，5 项真实 HTTP/生命周期检查通过。其余模块不因此通过验收。
