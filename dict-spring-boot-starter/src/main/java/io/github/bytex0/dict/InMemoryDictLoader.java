package io.github.bytex0.dict;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存字典(InMemoryDictLoader)可原子替换的数据源
 *
 * @author linshiqiang
 * @since 2026-10-05 16:08:16
 */
public class InMemoryDictLoader implements DictLoader {

    /**
     * 每种字典的不可变内容
     */
    private final Map<String, Map<String, String>> dictionaries = new ConcurrentHashMap<>();

    public void replace(String type, Map<String, String> values) {
        dictionaries.put(type, Map.copyOf(values));
    }

    @Override
    public Map<String, String> loadDict(String type) { return dictionaries.getOrDefault(type, Map.of()); }

    @Override
    public Map<String, Map<String, String>> loadAllDict() { return Map.copyOf(dictionaries); }
}
