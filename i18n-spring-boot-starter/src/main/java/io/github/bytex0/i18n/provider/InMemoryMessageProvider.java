package io.github.bytex0.i18n.provider;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存消息(InMemoryMessageProvider)不可变快照与原子更新
 *
 * @author linshiqiang
 * @since 2026-10-05 15:49:14
 */
public class InMemoryMessageProvider implements I18nMessageProvider {

    /**
     * 按语言保存不可变消息表
     */
    private final Map<Locale, Map<String, String>> messages = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> getMessages(Locale locale) {
        return messages.getOrDefault(locale, Map.of());
    }

    public void addMessage(Locale locale, String code, String message) {
        addMessages(locale, Map.of(code, message));
    }

    public void addMessages(Locale locale, Map<String, String> additions) {
        Map<String, String> snapshot = Map.copyOf(additions);
        messages.compute(locale, (key, previous) -> {
            Map<String, String> next = new HashMap<>(previous == null ? Map.of() : previous);
            next.putAll(snapshot);
            return Map.copyOf(next);
        });
    }

    public void removeMessage(Locale locale, String code) {
        messages.computeIfPresent(locale, (key, previous) -> {
            Map<String, String> next = new HashMap<>(previous);
            next.remove(code);
            return next.isEmpty() ? null : Map.copyOf(next);
        });
    }

    @Override
    public void refresh() {
        // 内存就是数据源，刷新不能删除已注册的业务消息。
    }
}
