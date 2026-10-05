package io.github.bytex0.dict.config;

import io.github.bytex0.dict.DictCache;
import io.github.bytex0.dict.DictLoader;
import io.github.bytex0.dict.InMemoryDictLoader;
import io.github.bytex0.dict.DictRefresher;
import io.github.bytex0.dict.properties.DictProperties;
import io.github.bytex0.dict.jackson.DictModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 字典(DictAutoConfiguration)自动配置，无数据源时使用内存加载器
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
@AutoConfiguration
@EnableConfigurationProperties(DictProperties.class)
@ConditionalOnProperty(prefix = "dict", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DictAutoConfiguration {

    /**
     * 默认内存来源，不访问数据库。
     *
     * @return 内存加载器
     */
    @Bean
    @ConditionalOnMissingBean(DictLoader.class)
    public InMemoryDictLoader dictLoader() {
        return new InMemoryDictLoader();
    }

    /**
     * 注册实例级缓存，只关联已有 JDBC 模板，不创建数据源。
     *
     * @param loader 字典来源
     * @param jdbc 可选数据库模板
     * @param properties 缓存配置
     * @return 缓存
     */
    @Bean
    @ConditionalOnMissingBean
    public DictCache dictCache(DictLoader loader, ObjectProvider<JdbcTemplate> jdbc, DictProperties properties) {
        return new DictCache(loader, jdbc.getIfAvailable(), properties.getMaxTypes());
    }

    /**
     * 保留此前缓存工厂入口，不附带数据库回退。
     *
     * @param loader 字典来源
     * @param environment 容量配置
     * @return 缓存
     */
    public DictCache dictCache(DictLoader loader, Environment environment) {
        return new DictCache(loader, environment.getProperty("dict.max-types", Integer.class, 1024));
    }

    /**
     * 注册 Jackson 3 属性增强模块，用户可覆盖。
     *
     * @param cache 当前缓存
     * @return 字典模块
     */
    @Bean
    @ConditionalOnMissingBean
    public DictModule dictModule(DictCache cache) {
        return new DictModule(cache);
    }

    /**
     * 保留原加载器和 JDBC 工厂入口，初始化仅影响新实例。
     *
     * @param loader 字典来源
     * @param jdbc 数据库回退
     * @return 已加载的字典模块
     */
    public DictModule dictModule(DictLoader loader, JdbcTemplate jdbc) {
        DictCache cache = new DictCache(loader, jdbc, new DictProperties().getMaxTypes());
        cache.refreshAll();
        return new DictModule(cache);
    }

    /**
     * 启动刷新由组件内的属性控制，关闭刷新不影响运行时按需查询。
     *
     * @param cache 当前缓存
     * @param properties 启动配置
     * @return 刷新组件
     */
    @Bean
    @ConditionalOnMissingBean
    public DictRefresher dictRefresher(DictCache cache, DictProperties properties) {
        return new DictRefresher(properties, cache);
    }

    /**
     * 保留原属性工厂入口，由容器绑定实例缓存。
     *
     * @param properties 启动配置
     * @return 刷新组件
     */
    public DictRefresher dictRefresher(DictProperties properties) {
        return new DictRefresher(properties);
    }

    /**
     * 保留此前缓存参数工厂入口。
     *
     * @param cache 当前缓存
     * @return 默认启动刷新组件
     */
    public DictRefresher dictRefresher(DictCache cache) {
        return new DictRefresher(new DictProperties(), cache);
    }
}
