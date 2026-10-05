package io.github.bytex0.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bytex0.cache.factory.LocalCaffeineCacheFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 本地缓存(LocalCacheConfiguration)自动配置
 *
 * @author linshiqiang
 * @since 2026-10-05 15:14:19
 */
@AutoConfiguration
@ConditionalOnClass(Caffeine.class)
@ConditionalOnProperty(prefix = "local-cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalCacheConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalCaffeineCacheFactory localCaffeineCacheFactory(ListableBeanFactory beanFactory) {
        return new LocalCaffeineCacheFactory(beanFactory);
    }
}
