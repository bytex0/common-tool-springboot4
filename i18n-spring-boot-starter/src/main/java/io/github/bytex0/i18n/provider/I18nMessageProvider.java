package io.github.bytex0.i18n.provider;

import java.util.Locale;
import java.util.Map;

/**
 * 国际化(I18nMessageProvider)消息来源接口
 *
 * @author linshiqiang
 * @since 2026-10-05 15:49:14
 */
public interface I18nMessageProvider {
    Map<String, String> getMessages(Locale locale);
    void refresh();
}
