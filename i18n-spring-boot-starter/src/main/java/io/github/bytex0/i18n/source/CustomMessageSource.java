package io.github.bytex0.i18n.source;

import io.github.bytex0.i18n.provider.I18nMessageProvider;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * 消息源(CustomMessageSource)复用Spring默认文本及格式化语义
 *
 * @author linshiqiang
 * @since 2026-10-05 15:49:14
 */
public class CustomMessageSource extends AbstractMessageSource {

    /**
     * 消息来源
     */
    private final I18nMessageProvider provider;

    /**
     * 缺失语言时使用的默认语言
     */
    private final Locale defaultLocale;

    public CustomMessageSource(I18nMessageProvider provider, boolean alwaysFormat, boolean codeAsDefault) {
        this(provider, alwaysFormat, codeAsDefault, Locale.ROOT);
    }

    public CustomMessageSource(I18nMessageProvider provider, boolean alwaysFormat, boolean codeAsDefault, Locale locale) {
        this.provider = provider;
        this.defaultLocale = locale;
        setAlwaysUseMessageFormat(alwaysFormat);
        setUseCodeAsDefaultMessage(codeAsDefault);
    }

    @Override
    protected String resolveCodeWithoutArguments(String code, Locale locale) {
        String value = provider.getMessages(locale).get(code);
        if (value == null && !locale.getCountry().isEmpty()) {
            value = provider.getMessages(Locale.forLanguageTag(locale.getLanguage())).get(code);
        }
        if (value == null && !locale.equals(defaultLocale)) {
            value = provider.getMessages(defaultLocale).get(code);
        }
        return value;
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        String value = resolveCodeWithoutArguments(code, locale);
        return value == null ? null : createMessageFormat(value, locale);
    }
}
