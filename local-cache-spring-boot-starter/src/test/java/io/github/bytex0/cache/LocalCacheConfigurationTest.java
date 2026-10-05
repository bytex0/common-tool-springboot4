package io.github.bytex0.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bytex0.cache.config.LocalCacheConfiguration;
import io.github.bytex0.cache.factory.LocalCaffeineCacheFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地缓存配置(LocalCacheConfigurationTest)Bean 生命周期及上下文隔离测试
 *
 * @author bytex0
 * @since 2026-10-05 15:16:06
 */
class LocalCacheConfigurationTest {

    /**
     * 自动配置测试上下文
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LocalCacheConfiguration.class));

    /**
     * 自动配置注册原缓存，注册快照不允许外部修改。
     */
    @Test
    void shouldRegisterImportsAndFactoryByDefault() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(LocalCacheConfiguration.class.getName());
        runner.withBean("demo", ConfiguredCache.class, this::cache).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LocalCaffeineCacheFactory.class);
            LocalCaffeineCacheFactory factory = context.getBean(LocalCaffeineCacheFactory.class);
            assertThat(factory.getCache(ConfiguredCache.class)).isSameAs(context.getBean("demo"));
            assertThat(context.getBean(ConfiguredCache.class).initializations).hasValue(1);
            assertThat(factory.getCache("unknown")).isNull();
            assertThat(factory.getCachesByType())
                    .containsEntry(ConfiguredCache.class, context.getBean("demo", ConfiguredCache.class));
            assertThatThrownBy(() -> factory.getAllCaches().clear()).isInstanceOf(UnsupportedOperationException.class);
        });
    }

    /**
     * 配置关闭和依赖缺失时均不创建缓存工厂。
     */
    @Test
    void shouldDisableFactoryAndBackOffWithoutCaffeine() {
        runner.withPropertyValues("local-cache.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LocalCaffeineCacheFactory.class));
        runner.withClassLoader(new FilteredClassLoader(Caffeine.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(LocalCaffeineCacheFactory.class));
    }

    /**
     * 用户提供的工厂不会被默认实现覆盖。
     */
    @Test
    void shouldRespectCustomFactory() {
        LocalCaffeineCacheFactory custom = new LocalCaffeineCacheFactory(new DefaultListableBeanFactory());
        runner.withBean(LocalCaffeineCacheFactory.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(LocalCaffeineCacheFactory.class);
            assertThat(context.getBean(LocalCaffeineCacheFactory.class)).isSameAs(custom);
        });
    }

    /**
     * 缓存在 Bean 后处理完成之后才初始化。
     */
    @Test
    void shouldRegisterAfterBeanPostProcessing() {
        runner.withBean("cachePostProcessor", BeanPostProcessor.class, () -> new BeanPostProcessor() {
            /**
             * {@inheritDoc}
             */
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ConfiguredCache cache) {
                    assertThat(cache.initializations).hasValue(0);
                }
                return bean;
            }
        }).withBean("demo", ConfiguredCache.class, this::cache)
                .run(context -> assertThat(context.getBean(ConfiguredCache.class).initializations).hasValue(1));
    }

    /**
     * 同类型多实例按名称保留，类型视图明确报告歧义。
     */
    @Test
    void shouldKeepSameTypeCachesByNameAndRejectAmbiguousTypeLookup() {
        runner.withBean("first", ConfiguredCache.class, this::cache)
                .withBean("second", ConfiguredCache.class, this::cache).run(context -> {
                    LocalCaffeineCacheFactory factory = context.getBean(LocalCaffeineCacheFactory.class);
                    assertThat(factory.getAllCaches()).containsOnlyKeys("first", "second");
                    assertThat(factory.getCache("first")).isNotSameAs(factory.getCache("second"));
                    assertThatThrownBy(() -> factory.getCache(ConfiguredCache.class))
                            .isInstanceOf(IllegalStateException.class);
                    assertThatThrownBy(factory::getCachesByType).isInstanceOf(IllegalStateException.class);
                    assertThatThrownBy(factory::getCacheStatsByClassName).isInstanceOf(IllegalStateException.class);
                });
    }

    /**
     * 独立上下文不共享状态，关闭后的工厂不能重新发布注册表。
     */
    @Test
    void shouldIsolateContextsAndClearRegistryOnClose() {
        AtomicReference<LocalCaffeineCacheFactory> closed = new AtomicReference<>();
        runner.withBean("demo", ConfiguredCache.class, this::cache).run(first -> {
            ConfiguredCache firstCache = first.getBean(ConfiguredCache.class);
            firstCache.put("key", "first");
            runner.withBean("demo", ConfiguredCache.class, this::cache).run(second -> {
                ConfiguredCache secondCache = second.getBean(ConfiguredCache.class);
                assertThat(secondCache.get("key")).isNull();
                secondCache.put("key", "second");
                assertThat(firstCache.get("key")).isEqualTo("first");
                closed.set(second.getBean(LocalCaffeineCacheFactory.class));
            });
            assertThat(closed.get().getAllCaches()).isEmpty();
            assertThatThrownBy(closed.get()::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class);
            assertThat(firstCache.get("key")).isEqualTo("first");
        });
    }

    /**
     * 原十三项统计保留且不可修改，类名视图和命名视图内容相同。
     */
    @Test
    void shouldExposeImmutableActualStatistics() {
        runner.withBean("demo", ConfiguredCache.class, this::cache).run(context -> {
            ConfiguredCache cache = context.getBean(ConfiguredCache.class);
            cache.get("key", () -> "value");
            cache.get("key");
            LocalCaffeineCacheFactory factory = context.getBean(LocalCaffeineCacheFactory.class);
            assertThat(factory.getCacheStats().get("demo")).containsEntry("loadSuccessCount", 1L)
                    .containsEntry("hitCount", 1L).containsEntry("missCount", 1L).containsEntry("hitRate", "50.00%");
            assertThat(factory.getCacheStatsByClassName().get("ConfiguredCache"))
                    .isEqualTo(factory.getCacheStats().get("demo"));
            assertThatThrownBy(() -> factory.getCacheStats().get("demo").clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            factory.clearAllCaches();
            assertThat(cache.size()).isZero();
        });
    }

    /**
     * 创建具有确定过期和容量的测试缓存。
     *
     * @return 未使用的缓存
     */
    private ConfiguredCache cache() {
        return new ConfiguredCache(Duration.ofMinutes(1), 10);
    }
}
