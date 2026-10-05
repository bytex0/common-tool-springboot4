package io.github.bytex0.i18n.service;

import io.github.bytex0.i18n.provider.I18nManager;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.Objects;

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

    /**
     * 注入实际消息源。
     *
     * @param source 非空消息源
     */
    public I18nService(MessageSource source) {
        this.source = Objects.requireNonNull(source);
    }

    /**
     * 保留原无格式参数入口，使用当前线程语言。
     *
     * @param code 消息编码
     * @return 消息内容
     */
    public String getMessage(String code) {
        return source.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * 按当前线程语言格式化消息；字符串参数应显式放入 Object[]，避免选中默认文本重载。
     *
     * @param code 消息编码
     * @param args 格式化参数
     * @return 消息内容
     */
    public String getMessage(String code, Object... args) {
        return getMessage(code, LocaleContextHolder.getLocale(), args);
    }

    /**
     * 保留原默认文本重载，找不到消息时使用提供的默认文本。
     *
     * @param code 消息编码
     * @param defaultMessage 默认文本，可为空
     * @param args 格式化参数
     * @return 消息或默认文本
     */
    public String getMessage(String code, String defaultMessage, Object... args) {
        return source.getMessage(code, args, defaultMessage, LocaleContextHolder.getLocale());
    }

    /**
     * 使用显式 Locale 查询。
     *
     * @param code 消息编码
     * @param locale 目标语言
     * @param args 格式化参数
     * @return 消息内容
     */
    public String getMessage(String code, Locale locale, Object... args) {
        return source.getMessage(code, args, locale);
    }

    /**
     * 保留指定 Locale 和默认文本的原重载。
     *
     * @param code 消息编码
     * @param locale 目标语言
     * @param defaultMessage 默认文本
     * @param args 格式化参数
     * @return 消息或默认文本
     */
    public String getMessage(String code, Locale locale, String defaultMessage, Object... args) {
        return source.getMessage(code, args, defaultMessage, locale);
    }

    /**
     * 通过语言标签查询，空标签按原行为使用当前线程语言。
     *
     * @param code 消息编码
     * @param locale 支持 en_US、en-US，空值使用当前语言
     * @param args 格式化参数
     * @return 消息内容
     */
    public String getMessageByLocale(String code, String locale, Object... args) {
        return getMessage(code, locale(locale), args);
    }

    /**
     * 保留语言标签加默认文本的原入口。
     *
     * @param code 消息编码
     * @param locale 语言标签，空值使用当前语言
     * @param defaultMessage 默认文本
     * @param args 格式化参数
     * @return 消息或默认文本
     */
    public String getMessageByLocale(String code, String locale, String defaultMessage, Object... args) {
        return source.getMessage(code, args, defaultMessage, locale(locale));
    }

    /**
     * 保留新版本中不与字符串格式参数混淆的默认文本入口。
     *
     * @param code 消息编码
     * @param defaultMessage 默认文本
     * @param locale 目标语言
     * @param args 格式化参数
     * @return 消息或默认文本
     */
    public String getOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
        return source.getMessage(code, args, defaultMessage, locale);
    }

    /**
     * 将查询语言标签映射为 Locale，与管理入口的 ROOT 语义区分。
     *
     * @param value 可为空的语言标签
     * @return 显式语言或当前线程语言
     */
    private Locale locale(String value) {
        return value == null || value.isBlank() ? LocaleContextHolder.getLocale() : I18nManager.parseLocale(value);
    }
}
