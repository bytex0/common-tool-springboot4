package io.github.bytex0.i18n.provider;

import java.util.Locale;
import java.util.IllformedLocaleException;
import java.util.Map;
import java.util.Objects;

/**
 * 国际化管理(I18nManager)动态消息维护
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
public class I18nManager {

    /**
     * 当前消息提供器
     */
    private final I18nMessageProvider provider;

    /**
     * 注入消息提供器，不在构造时清空业务数据。
     *
     * @param provider 非空提供器
     */
    public I18nManager(I18nMessageProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    /**
     * 新增或替换内存消息。
     *
     * @param locale 语言标签，空字符串表示 ROOT
     * @param code 消息编码
     * @param message 消息内容
     */
    public void addMessage(String locale, String code, String message) {
        memory().addMessage(parseLocale(locale), code, message);
    }

    /**
     * 批量原子更新某种语言的消息。
     *
     * @param locale 语言标签
     * @param messages 消息集合
     */
    public void addMessages(String locale, Map<String, String> messages) {
        memory().addMessages(parseLocale(locale), messages);
    }

    /**
     * 删除指定内存消息。
     *
     * @param locale 语言标签
     * @param code 消息编码
     */
    public void removeMessage(String locale, String code) {
        memory().removeMessage(parseLocale(locale), code);
    }

    /**
     * 刷新提供器派生缓存，不删除作为数据源的内存消息。
     */
    public void refresh() {
        provider.refresh();
    }

    /**
     * 明确清空内存消息，保留原内存刷新具有的清空能力，但不混淆刷新与删除。
     */
    public void clear() {
        memory().clear();
    }

    /**
     * 显式清空单个语言，保留其他语言的数据。
     *
     * @param locale 目标语言标签，空白为 ROOT
     */
    public void clear(String locale) {
        memory().clear(parseLocale(locale));
    }

    /**
     * 解析显式语言标签，管理入口允许 ROOT，不静默截断非法标签。
     *
     * @param locale 非空字符串，空白表示 ROOT
     * @return 对应 Locale
     */
    public static Locale parseLocale(String locale) {
        if (locale == null) {
            throw new IllegalArgumentException("语言标签不能为空");
        }
        if (locale.isBlank()) {
            return Locale.ROOT;
        }
        try {
            return new Locale.Builder().setLanguageTag(locale.replace('_', '-')).build();
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("无效的语言标签", exception);
        }
    }

    /**
     * 校验当前数据源支持写入，避免对只读资源的修改被静默忽略。
     *
     * @return 内存消息提供器
     */
    private InMemoryMessageProvider memory() {
        if (provider instanceof InMemoryMessageProvider memory) {
            return memory;
        }
        throw new UnsupportedOperationException("当前消息来源不支持运行时修改");
    }
}
