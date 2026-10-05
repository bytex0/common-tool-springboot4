package io.github.bytex0.i18n.service;

import io.github.bytex0.i18n.provider.I18nManager;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * 国际化服务(I18nService)当前语言及指定语言消息查询
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
public class I18nService {

    /**
     * Spring消息源
     */
    private final MessageSource source;

    public I18nService(MessageSource source) { this.source = source; }

    public String getMessage(String code, Object... args) {
        return getMessage(code, LocaleContextHolder.getLocale(), args);
    }

    public String getMessage(String code, Locale locale, Object... args) {
        return source.getMessage(code, args, locale);
    }

    public String getMessageByLocale(String code, String locale, Object... args) {
        return getMessage(code, I18nManager.parseLocale(locale), args);
    }

    public String getOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
        return source.getMessage(code, args, defaultMessage, locale);
    }
}
