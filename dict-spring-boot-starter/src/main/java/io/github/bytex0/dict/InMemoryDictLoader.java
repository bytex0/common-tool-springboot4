package io.github.bytex0.dict;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.util.Assert;

/**
 * 内存字典(InMemoryDictLoader)可原子替换的数据源
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
public class InMemoryDictLoader implements DictLoader {

    /**
     * 每种字典的不可变内容
     */
    private final Map<String, Map<String, String>> dictionaries = new ConcurrentHashMap<>();

    /**
     * 原子替换单个字典来源，缓存需通过 refresh 或 refreshAll 显式刷新。
     *
     * @param type 非空类型
     * @param values 不含 null 键值的字典
     */
    public void replace(String type, Map<String, String> values) {
        Assert.hasText(type, "字典类型不能为空");
        Assert.notNull(values, "字典数据不能为空");
        values.forEach((code, text) -> {
            Assert.notNull(code, "字典编码不能为null");
            Assert.notNull(text, "字典文本不能为null");
        });
        dictionaries.put(type, Map.copyOf(values));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> loadDict(String type) {
        return dictionaries.getOrDefault(type, Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Map<String, String>> loadAllDict() {
        return Map.copyOf(dictionaries);
    }
}
