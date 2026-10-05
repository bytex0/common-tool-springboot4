package io.github.bytex0.i18n;

import io.github.bytex0.i18n.config.I18nAutoConfiguration;
import io.github.bytex0.i18n.provider.I18nManager;
import io.github.bytex0.i18n.provider.I18nMessageProvider;
import io.github.bytex0.i18n.provider.InMemoryMessageProvider;
import io.github.bytex0.i18n.provider.ResourceBundleMessageProvider;
import io.github.bytex0.i18n.service.I18nService;
import io.github.bytex0.i18n.source.CustomMessageSource;
import io.github.bytex0.i18n.properties.I18nProperties;
import io.github.bytex0.i18n.resolver.CustomLocaleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.mock.web.MockHttpServletRequest;

import java.beans.Introspector;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 国际化(I18nTest)消息语义、资源回退及刷新隔离测试
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
class I18nTest {

    /**
     * 临时资源目录
     */
    @TempDir
    Path directory;

    /**
     * 开关、用户提供器覆盖及错误配置均有明确行为。
     */
    @Test
    void shouldConfigureOnlyWhenEnabledAndSupportOverride() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(I18nService.class));
        InMemoryMessageProvider custom = new InMemoryMessageProvider();
        runner.withPropertyValues("i18n.enabled=true").withBean(I18nMessageProvider.class, () -> custom)
                .run(context -> assertThat(context.getBean(I18nMessageProvider.class)).isSameAs(custom));
        runner.withPropertyValues("i18n.enabled=true", "i18n.provider=unsupported")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 格式化和显式默认文本优先级遵守 Spring 消息契约。
     */
    @Test
    void shouldFormatAndRespectExplicitDefaultBeforeCode() {
        InMemoryMessageProvider provider = new InMemoryMessageProvider();
        provider.addMessage(Locale.ENGLISH, "hello", "Hello {0}");
        CustomMessageSource source = new CustomMessageSource(provider, false, true);
        assertThat(source.getMessage("hello", new Object[]{"Lin"}, Locale.US)).isEqualTo("Hello Lin");
        assertThat(source.getMessage("missing", null, "fallback", Locale.US)).isEqualTo("fallback");
        assertThat(source.getMessage("missing", null, Locale.US)).isEqualTo("missing");
        CustomMessageSource strict = new CustomMessageSource(provider, false, false);
        assertThatThrownBy(() -> strict.getMessage("missing", null, Locale.US)).isInstanceOf(NoSuchMessageException.class);
    }

    /**
     * 保留 MessageSourceResolvable 多编码和默认文本能力，空编码数组不发生越界。
     * 强制格式化开关应在没有参数时仍生效。
     */
    @Test
    void shouldHandleResolvableAndAlwaysFormatContracts() {
        InMemoryMessageProvider provider = new InMemoryMessageProvider();
        provider.addMessages(Locale.ENGLISH, Map.of("hello", "Hello {0}", "quote", "It''s ready"));
        CustomMessageSource source = new CustomMessageSource(provider, false, false);
        assertThat(source.getMessage(new DefaultMessageSourceResolvable(
                new String[]{"absent", "hello"}, new Object[]{"Lin"}, "fallback"), Locale.US)).isEqualTo("Hello Lin");
        assertThat(source.getMessage(new DefaultMessageSourceResolvable(
                new String[]{}, new Object[]{}, "empty fallback"), Locale.US)).isEqualTo("empty fallback");
        assertThatThrownBy(() -> source.getMessage(new DefaultMessageSourceResolvable(new String[]{}), Locale.US))
                .isInstanceOf(NoSuchMessageException.class);
        assertThat(source.getMessage("quote", null, Locale.US)).isEqualTo("It''s ready");
        assertThat(new CustomMessageSource(provider, true, false).getMessage("quote", null, Locale.US))
                .isEqualTo("It's ready");
    }

    /**
     * 内存刷新不删除数据，写入后旧快照仍保持不变。
     */
    @Test
    void shouldKeepMemoryMessagesOnRefreshAndExposeSnapshots() {
        InMemoryMessageProvider provider = new InMemoryMessageProvider();
        I18nManager manager = new I18nManager(provider);
        manager.addMessage("en_US", "key", "first");
        Map<String, String> before = provider.getMessages(Locale.US);
        manager.addMessage("en-US", "key", "second");
        manager.refresh();
        assertThat(before).containsEntry("key", "first");
        assertThat(provider.getMessages(Locale.US)).containsEntry("key", "second");
        assertThatThrownBy(() -> before.clear()).isInstanceOf(UnsupportedOperationException.class);
        manager.removeMessage("en-US", "key");
        assertThat(provider.getMessages(Locale.US)).isEmpty();
    }

    /**
     * UTF-8 资源支持父级回退和明确刷新，资源提供器不能冒充可写存储。
     *
     * @throws Exception 临时文件或类加载器操作失败
     */
    @Test
    void shouldLoadUtf8FallbackAndRefreshResources() throws Exception {
        Files.writeString(directory.resolve("messages.properties"), "base=根消息\n");
        Files.writeString(directory.resolve("messages_en.properties"), "hello=Hello\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()})) {
            ResourceBundleMessageProvider provider = new ResourceBundleMessageProvider("messages", -1, loader);
            assertThat(provider.getMessages(Locale.US)).containsEntry("base", "根消息").containsEntry("hello", "Hello");
            Files.writeString(directory.resolve("messages_en.properties"), "hello=Changed\n");
            assertThat(provider.getMessages(Locale.US)).containsEntry("hello", "Hello");
            provider.refresh();
            assertThat(provider.getMessages(Locale.US)).containsEntry("hello", "Changed");
            assertThatThrownBy(() -> new I18nManager(provider).addMessage("en", "x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    /**
     * 零缓存时长立即读取新数据，非法语言标签不被静默截断。
     *
     * @throws Exception 临时资源操作失败
     */
    @Test
    void shouldHonorZeroCacheDurationAndLanguageValidation() throws Exception {
        Files.writeString(directory.resolve("messages.properties"), "key=first\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()})) {
            ResourceBundleMessageProvider provider = new ResourceBundleMessageProvider("messages", 0, loader);
            assertThat(provider.getMessages(Locale.ROOT)).containsEntry("key", "first");
            Files.writeString(directory.resolve("messages.properties"), "key=second\n");
            assertThat(provider.getMessages(Locale.ROOT)).containsEntry("key", "second");
        }
        assertThat(I18nManager.parseLocale("zh_CN")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(I18nManager.parseLocale("en-US")).isEqualTo(Locale.US);
        assertThatThrownBy(() -> I18nManager.parseLocale("en;invalid")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 原七种服务重载继续可用，空查询语言使用当前线程语言，新增明确默认文本入口也保留。
     */
    @Test
    void shouldSupportOriginalMessageOverloads() {
        InMemoryMessageProvider provider = new InMemoryMessageProvider();
        provider.addMessages(Locale.ENGLISH, Map.of("hello", "Hello {0}", "plain", "Hello"));
        I18nService service = new I18nService(new CustomMessageSource(provider, false, true));
        LocaleContextHolder.setLocale(Locale.US);
        try {
            assertThat(service.getMessage("plain")).isEqualTo("Hello");
            assertThat(service.getMessage("hello", new Object[]{"Lin"})).isEqualTo("Hello Lin");
            assertThat(service.getMessage("missing", "fallback")).isEqualTo("fallback");
            assertThat(service.getMessage("hello", Locale.US, new Object[]{"Lin"})).isEqualTo("Hello Lin");
            assertThat(service.getMessage("missing", Locale.US, "fallback")).isEqualTo("fallback");
            assertThat(service.getMessageByLocale("hello", "en_US", new Object[]{"Lin"})).isEqualTo("Hello Lin");
            assertThat(service.getMessageByLocale("missing", "en-US", "fallback")).isEqualTo("fallback");
            assertThat(service.getMessageByLocale("hello", "", new Object[]{"Lin"})).isEqualTo("Hello Lin");
            assertThat(service.getOrDefault("missing", "explicit", Locale.US)).isEqualTo("explicit");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /**
     * 原只实现 getMessages 的函数式提供器仍能使用，内存删除能力以 clear 显式保留。
     */
    @Test
    void shouldPreserveProviderExtensionAndExplicitClear() {
        I18nMessageProvider readOnly = locale -> Map.of("key", "value");
        readOnly.refresh();
        assertThat(readOnly.getMessages(Locale.ROOT)).containsEntry("key", "value");
        InMemoryMessageProvider provider = new InMemoryMessageProvider();
        I18nManager manager = new I18nManager(provider);
        manager.addMessage("", "root", "root-value");
        manager.addMessage("en", "key", "value");
        Map<String, String> before = provider.getMessages(Locale.ROOT);
        manager.refresh();
        assertThat(provider.getMessages(Locale.ROOT)).containsEntry("root", "root-value");
        manager.clear("en");
        assertThat(provider.getMessages(Locale.ENGLISH)).isEmpty();
        assertThat(provider.getMessages(Locale.ROOT)).containsEntry("root", "root-value");
        manager.clear();
        assertThat(provider.getMessages(Locale.ROOT)).isEmpty();
        assertThat(provider.getMessages(Locale.ENGLISH)).isEmpty();
        assertThat(before).containsEntry("root", "root-value");
    }

    /**
     * 原 ROOT 文件和脚本标签精确文件继续覆盖相同编码，不丢失标准回退的新能力。
     *
     * @throws Exception 资源操作失败
     */
    @Test
    void shouldSupportOriginalResourceNames() throws Exception {
        Files.writeString(directory.resolve("messages.properties"), "base=base-value\nkey=standard\n");
        Files.writeString(directory.resolve("messages_.properties"), "key=原ROOT文件\n");
        Locale scriptLocale = new Locale.Builder().setLanguage("en").setRegion("US").setScript("Latn").build();
        Files.writeString(directory.resolve("messages_" + scriptLocale + ".properties"), "key=原脚本标签文件\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()})) {
            ResourceBundleMessageProvider provider = new ResourceBundleMessageProvider("messages", 0, loader);
            assertThat(provider.getMessages(Locale.ROOT)).containsEntry("key", "原ROOT文件")
                    .containsEntry("base", "base-value");
            assertThat(provider.getMessages(scriptLocale)).containsEntry("key", "原脚本标签文件");
        }
    }

    /**
     * 资源打开失败不能伪装成没有翻译。
     *
     * @throws Exception 创建测试 URL 失败
     */
    @Test
    void shouldPropagateResourceReadFailure() throws Exception {
        URL missing = directory.resolve("does-not-exist.properties").toUri().toURL();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            /**
             * 模拟已发现地址但读取时文件不存在的资源。
             *
             * @param name 请求资源名
             * @return 不可读取的测试 URL
             */
            @Override
            public URL getResource(String name) {
                return missing;
            }
        };
        ResourceBundleMessageProvider provider = new ResourceBundleMessageProvider("messages", 0, loader);
        assertThatThrownBy(() -> provider.getMessages(Locale.ENGLISH)).isInstanceOf(UncheckedIOException.class);
    }

    /**
     * 原 Boolean 模型保持可写，默认提供器及缓存值不改变。
     *
     * @throws Exception JavaBeans 内省失败
     */
    @Test
    void shouldKeepConfigurationBeanContract() throws Exception {
        I18nProperties properties = new I18nProperties();
        assertThat(properties.getEnabled()).isFalse();
        assertThat(properties.getCacheSeconds()).isEqualTo(3600);
        assertThat(properties).isEqualTo(new I18nProperties());
        for (String name : new String[]{"enabled", "alwaysUseMessageFormat", "useCodeAsDefaultMessage"}) {
            var descriptor = Arrays.stream(Introspector.getBeanInfo(I18nProperties.class).getPropertyDescriptors())
                    .filter(property -> property.getName().equals(name)).findFirst().orElseThrow();
            assertThat(descriptor.getPropertyType()).isEqualTo(Boolean.class);
            assertThat(descriptor.getWriteMethod()).isNotNull();
        }
    }

    /**
     * 并发更新同一语言不丢失编码，清空不会修改之前返回的快照。
     *
     * @throws Exception 并发任务失败
     */
    @Test
    void shouldKeepConcurrentMemoryUpdatesAndSnapshots() throws Exception {
        InMemoryMessageProvider provider = new InMemoryMessageProvider();
        provider.addMessage(Locale.ENGLISH, "seed", "value");
        Map<String, String> snapshot = provider.getMessages(Locale.ENGLISH);
        try (ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), Thread.ofPlatform().daemon(true).factory())) {
            var tasks = new ArrayList<Future<?>>();
            for (int index = 0; index < 64; index++) {
                int position = index;
                tasks.add(executor.submit(() -> provider.addMessage(Locale.ENGLISH,
                        "key-" + position, "value-" + position)));
            }
            for (Future<?> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        }
        assertThat(provider.getMessages(Locale.ENGLISH)).hasSize(65);
        provider.clear();
        assertThat(provider.getMessages(Locale.ENGLISH)).isEmpty();
        assertThat(snapshot).containsOnlyKeys("seed");
    }

    /**
     * 保留原解析器构造和接口，非 Web 应用不因解析器类存在而依赖 MVC。
     */
    @Test
    void shouldKeepResolverAndNonWebSupport() {
        CustomLocaleResolver resolver = new CustomLocaleResolver("zh_CN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        request.addPreferredLocale(Locale.US);
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
        MockHttpServletRequest legacy = new MockHttpServletRequest();
        legacy.addHeader("Accept-Language", "en_US");
        assertThat(resolver.resolveLocale(legacy)).isEqualTo(Locale.US);
        resolver.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE));
        assertThat(resolver.resolveLocale(legacy)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThatThrownBy(() -> resolver.setLocale(request, null, Locale.GERMANY))
                .isInstanceOf(UnsupportedOperationException.class);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader("org.springframework.web", "jakarta.servlet"))
                .withPropertyValues("i18n.enabled=true", "i18n.provider=memory")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(I18nService.class));
    }
}
