package io.github.bytex0.i18n;

import io.github.bytex0.i18n.config.I18nAutoConfiguration;
import io.github.bytex0.i18n.provider.I18nManager;
import io.github.bytex0.i18n.provider.I18nMessageProvider;
import io.github.bytex0.i18n.provider.InMemoryMessageProvider;
import io.github.bytex0.i18n.provider.ResourceBundleMessageProvider;
import io.github.bytex0.i18n.service.I18nService;
import io.github.bytex0.i18n.source.CustomMessageSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.NoSuchMessageException;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 国际化(I18nTest)消息语义、资源回退及刷新隔离测试
 *
 * @author linshiqiang
 * @since 2026-10-05 15:49:14
 */
class I18nTest {

    /**
     * 临时资源目录
     */
    @TempDir
    Path directory;

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
}
