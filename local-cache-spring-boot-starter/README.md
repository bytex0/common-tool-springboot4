# Local Cache Spring Boot 4 Starter

基于 Caffeine 的本地缓存。除 Boot 4 适配外，本次修复原实现中构造阶段过早初始化、静态注册表跨上下文共享及统计未开启的问题。

## 接入

导入本项目 BOM 后添加：

```xml
<dependency>
    <groupId>io.github.bytex0</groupId>
    <artifactId>local-cache-spring-boot4-starter</artifactId>
</dependency>
```

配置项 `local-cache.enabled` 默认为 `true`，控制缓存工厂是否装配，不影响独立创建和调用缓存实例。Starter 不引入 Web、Redis 或数据库。

缓存实现继承 `io.github.bytex0.cache.core.AbstractLocalCaffeineCache<K, V>`，实现以下配置方法并将类注册为 Spring 单例 Bean：

- `getExpireAfterAccess()`：距离最后访问的过期时间。
- `getMaximumSize()`：最大条目数。
- `getInitialCapacity()`：初始容量。
- 可选覆盖 `onRemoval()` 处理移除回调。

缓存会在所有普通单例初始化完成后由工厂初始化。独立 `new` 出来的非 Spring 实例在第一次访问时初始化，也可在子类构造结束后调用 `initialize()`。不要在子类构造函数中提前调用缓存操作；这些方法可能需要尚未就绪的配置。

## 使用与语义

直接注入业务缓存 Bean，或通过构造器注入 `LocalCaffeineCacheFactory` 查找：

- `getCache(MyCache.class)`：不存在时返回 null，多个同类型缓存时报错，不再静默覆盖。
- `getCache("beanName")`：按 Spring Bean 名称查找。
- `getAllCaches()`：返回不可修改的命名注册表。
- `clearAllCaches()`：清空当前上下文的缓存条目。
- `getCacheStats()`：按 Bean 名称返回大小、命中、缺失、加载、失败和淘汰统计。

缓存 API 保留 `get`、`put`、`remove`、`clear`、`size`、`stats`。`get(key, supplier)` 使用 Caffeine 原子加载，同一个键并发缺失时只计算一次；加载抛出的异常会传播，null 和失败不会写入缓存，后续可重试。

`size()` 是 Caffeine 的估算大小。过期、容量淘汰及移除通知不保证立即全部完成，需要显式维护时调用 `cleanUp()`。`clear()` 会清空条目并执行维护，但不会重置累计统计。移除回调由 Caffeine 执行，不应依赖回调与调用方同步完成。

时间、容量、初始容量由 Caffeine 校验；零过期时间或零容量沿用 Caffeine 语义。默认启用 `recordStats()`，不再返回没有采集意义的全零统计。自定义 `createCache()` 时应自行保留需要的过期、容量和统计策略。

工厂在启动时收集可识别类型的单例缓存，不注册 prototype，也不持续扫描运行时新增的 Bean。缓存按上下文隔离，无跨上下文静态状态；上下文关闭时清空本上下文缓存及注册表。它不替代 Redis、分布式缓存或 Spring Cache 注解集成。

## 兼容变化

本轮完整对照见 [MIGRATION.md](MIGRATION.md)。增加 `getCachesByType()` 类型视图和
`getCacheStatsByClassName()` 原类名统计视图。初始化使用显式锁，递归创建立即失败，关闭后不允许重新发布工厂注册表。
库和示例均接入包含测试源码的 Checkstyle。

- 根包改为 `io.github.bytex0.cache`。
- 原工厂的静态方法改为实例方法，请注入工厂，不再使用 `LocalCaffeineCacheFactory.getCache(...)`。
- `getAllCaches()` 和统计的键改为 Bean 名称，防止同类型或同简单类名实例覆盖。
- 初始化不再发生在父类字段初始化或 BeanFactoryPostProcessor 阶段，子类构造参数和常规依赖注入可安全用于配置。
- 默认统计现在真实开启；命中率使用固定 Locale 格式化。

## 验证

```bash
mvn -pl local-cache-spring-boot-starter -am test
python3 scripts/test-starter.py local-cache
```

单元测试使用虚拟时钟验证访问过期，使用并发调用验证同键只加载一次，并覆盖容量淘汰、初始化、失败、空值、配置开关、工厂覆盖和上下文隔离。

真实测试接口与自动化说明见 [示例 README](../examples-starter/local-cache-example/README.md)。
