package io.github.bytex0.dict.config;

import io.github.bytex0.dict.DictCache;
import io.github.bytex0.dict.DictLoader;
import io.github.bytex0.dict.InMemoryDictLoader;
import io.github.bytex0.dict.jackson.DictModule;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 字典(DictAutoConfiguration)自动配置，无数据源时使用内存加载器
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "dict", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DictAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DictLoader.class)
    public InMemoryDictLoader dictLoader() { return new InMemoryDictLoader(); }

    @Bean
    @ConditionalOnMissingBean
    public DictCache dictCache(DictLoader loader, Environment environment) {
        return new DictCache(loader, environment.getProperty("dict.max-types", Integer.class, 1024));
    }

    @Bean
    @ConditionalOnMissingBean
    public DictModule dictModule(DictCache cache) { return new DictModule(cache); }

    @Bean
    @ConditionalOnProperty(prefix = "dict", name = "auto-refresh", havingValue = "true", matchIfMissing = true)
    public SmartInitializingSingleton dictRefresher(DictCache cache) { return cache::refreshAll; }
}
