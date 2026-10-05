package io.github.bytex0.i18n.provider;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 内存消息(InMemoryMessageProvider)不可变快照与原子更新
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
public class InMemoryMessageProvider implements I18nMessageProvider {

    /**
     * 通过整体不可变快照原子发布更新和清空，旧读取结果不被改变。
     */
    private final AtomicReference<Map<Locale, Map<String, String>>> messages = new AtomicReference<>(Map.of());

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getMessages(Locale locale) {
        return messages.get().getOrDefault(Objects.requireNonNull(locale), Map.of());
    }

    /**
     * 更新单条消息。
     *
     * @param locale 目标语言
     * @param code 非空编码
     * @param message 非空内容
     */
    public void addMessage(Locale locale, String code, String message) {
        addMessages(locale, Map.of(code, message));
    }

    /**
     * 原子合并一批消息，失败时不发布部分更新。
     *
     * @param locale 非空语言
     * @param additions 不含空键值的消息集合
     */
    public void addMessages(Locale locale, Map<String, String> additions) {
        Objects.requireNonNull(locale);
        Map<String, String> snapshot = Map.copyOf(additions);
        messages.updateAndGet(previous -> {
            Map<String, String> entries = new HashMap<>(previous.getOrDefault(locale, Map.of()));
            entries.putAll(snapshot);
            Map<Locale, Map<String, String>> next = new HashMap<>(previous);
            next.put(locale, Map.copyOf(entries));
            return Map.copyOf(next);
        });
    }

    /**
     * 原子删除消息，语言尚无数据或编码不存在时不引入新条目。
     *
     * @param locale 非空语言
     * @param code 非空编码
     */
    public void removeMessage(Locale locale, String code) {
        Objects.requireNonNull(locale);
        Objects.requireNonNull(code);
        messages.updateAndGet(previous -> {
            Map<Locale, Map<String, String>> next = new HashMap<>(previous);
            Map<String, String> entries = new HashMap<>(previous.getOrDefault(locale, Map.of()));
            entries.remove(code);
            if (entries.isEmpty()) {
                next.remove(locale);
            } else {
                next.put(locale, Map.copyOf(entries));
            }
            return Map.copyOf(next);
        });
    }

    /**
     * 内存本身是数据源，没有可重新加载的派生缓存；刷新保留数据。
     */
    @Override
    public void refresh() {
        // 内存就是数据源，刷新不能删除已注册的业务消息。
    }

    /**
     * 原子清空全部内存数据，不影响已经返回的快照。
     */
    public void clear() {
        messages.set(Map.of());
    }

    /**
     * 原子清空一种语言的数据，替代修改公开 Map 的不安全做法。
     *
     * @param locale 非空目标语言
     */
    public void clear(Locale locale) {
        Objects.requireNonNull(locale);
        messages.updateAndGet(previous -> {
            Map<Locale, Map<String, String>> next = new HashMap<>(previous);
            next.remove(locale);
            return Map.copyOf(next);
        });
    }
}
