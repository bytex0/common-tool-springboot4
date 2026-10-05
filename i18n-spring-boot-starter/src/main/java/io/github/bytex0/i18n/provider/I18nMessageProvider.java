package io.github.bytex0.i18n.provider;

import java.util.Locale;
import java.util.Map;

/**
 * 国际化(I18nMessageProvider)消息来源接口
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
@FunctionalInterface
public interface I18nMessageProvider {

    /**
     * 返回某个语言的消息快照。
     *
     * @param locale 非空语言
     * @return 消息编码与文本映射
     */
    Map<String, String> getMessages(Locale locale);

    /**
     * 刷新派生缓存，保留原接口默认实现，使只实现读取的用户提供器仍可使用。
     */
    default void refresh() {
        // 没有派生缓存的提供器不需要刷新。
    }
}
