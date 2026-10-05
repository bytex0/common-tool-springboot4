package io.github.bytex0.i18n.source;

import io.github.bytex0.i18n.provider.I18nMessageProvider;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 消息源(CustomMessageSource)复用Spring默认文本及格式化语义
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
public class CustomMessageSource extends AbstractMessageSource {

    /**
     * 使用标准 Locale 层级，不采用机器默认语言作为候选。
     */
    private static final ResourceBundle.Control LOCALE_CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    /**
     * 消息来源
     */
    private final I18nMessageProvider provider;

    /**
     * 缺失语言时使用的默认语言
     */
    private final Locale defaultLocale;

    /**
     * 保留原三参数构造方式。
     *
     * @param provider 消息来源
     * @param alwaysFormat 是否强制应用格式化
     * @param codeAsDefault 是否使用编码作为最后回退
     */
    public CustomMessageSource(I18nMessageProvider provider, boolean alwaysFormat, boolean codeAsDefault) {
        this(provider, alwaysFormat, codeAsDefault, Locale.ROOT);
    }

    /**
     * 创建带应用默认语言回退的消息源，默认文本等契约由 Spring 实现。
     *
     * @param provider 消息来源
     * @param alwaysFormat 是否强制格式化
     * @param codeAsDefault 是否使用编码回退
     * @param locale 最后的默认语言
     */
    public CustomMessageSource(I18nMessageProvider provider, boolean alwaysFormat, boolean codeAsDefault, Locale locale) {
        this.provider = Objects.requireNonNull(provider);
        this.defaultLocale = Objects.requireNonNull(locale);
        setAlwaysUseMessageFormat(alwaysFormat);
        setUseCodeAsDefaultMessage(codeAsDefault);
    }

    /**
     * 按请求语言、标准父级和 ROOT 查找，仍缺失时使用应用默认语言。
     *
     * @param code 消息编码
     * @param locale 请求语言
     * @return 消息文本，缺失时为 null
     */
    @Override
    protected String resolveCodeWithoutArguments(String code, Locale locale) {
        String value = provider.getMessages(locale).get(code);
        if (value == null) {
            for (Locale candidate : LOCALE_CONTROL.getCandidateLocales("messages", locale)) {
                if (!candidate.equals(locale)) {
                    value = provider.getMessages(candidate).get(code);
                    if (value != null) {
                        break;
                    }
                }
            }
        }
        if (value == null && !locale.equals(defaultLocale)) {
            value = provider.getMessages(defaultLocale).get(code);
        }
        return value;
    }

    /**
     * 为当前调用创建独立 MessageFormat，避免跨线程共享格式化器。
     *
     * @param code 消息编码
     * @param locale 格式化语言
     * @return 格式化器，缺失时为 null
     */
    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        String value = resolveCodeWithoutArguments(code, locale);
        return value == null ? null : createMessageFormat(value, locale);
    }
}
