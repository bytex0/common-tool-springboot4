package io.github.bytex0.i18n.provider;

import java.util.Locale;
import java.util.IllformedLocaleException;
import java.util.Map;

/**
 * 国际化管理(I18nManager)动态消息维护
 *
 * @author linshiqiang
 * @since 2026-10-05 15:49:14
 */
public class I18nManager {

    /**
     * 当前消息提供器
     */
    private final I18nMessageProvider provider;

    public I18nManager(I18nMessageProvider provider) { this.provider = provider; }

    public void addMessage(String locale, String code, String message) {
        memory().addMessage(parseLocale(locale), code, message);
    }

    public void addMessages(String locale, Map<String, String> messages) {
        memory().addMessages(parseLocale(locale), messages);
    }

    public void removeMessage(String locale, String code) {
        memory().removeMessage(parseLocale(locale), code);
    }

    public void refresh() { provider.refresh(); }

    public static Locale parseLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException("语言标签不能为空");
        }
        try {
            return new Locale.Builder().setLanguageTag(locale.replace('_', '-')).build();
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("无效的语言标签", exception);
        }
    }

    private InMemoryMessageProvider memory() {
        if (provider instanceof InMemoryMessageProvider memory) {
            return memory;
        }
        throw new UnsupportedOperationException("当前消息来源不支持运行时修改");
    }
}
