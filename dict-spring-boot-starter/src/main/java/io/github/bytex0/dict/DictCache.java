package io.github.bytex0.dict;

import org.springframework.util.Assert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字典缓存(DictCache)上下文隔离、不可变快照及失败保留
 *
 * @author linshiqiang
 * @since 2026-10-05 16:08:16
 */
public class DictCache {

    /**
     * 业务字典来源
     */
    private final DictLoader loader;

    /**
     * 最多缓存的字典类型数
     */
    private final int maxTypes;

    /**
     * 原子替换的快照，读取无锁
     */
    private volatile Map<String, Map<String, String>> snapshot = Map.of();

    public DictCache(DictLoader loader, int maxTypes) {
        Assert.isTrue(maxTypes > 0, "dict.max-types必须大于0");
        this.loader = loader;
        this.maxTypes = maxTypes;
    }

    public String getDictText(String type, String value) {
        if (value == null) { return null; }
        Assert.hasText(type, "字典类型不能为空");
        Map<String, String> values = snapshot.get(type);
        if (values == null) {
            synchronized (this) {
                values = snapshot.get(type);
                if (values == null) {
                    Map<String, String> loaded = loader.loadDict(type);
                    values = loaded == null ? Map.of() : Map.copyOf(loaded);
                    refresh(type, values);
                }
            }
        }
        return values.get(value);
    }

    public synchronized void refresh(String type, Map<String, String> values) {
        Assert.hasText(type, "字典类型不能为空");
        Map<String, Map<String, String>> next = new LinkedHashMap<>(snapshot);
        next.put(type, Map.copyOf(values));
        Assert.isTrue(next.size() <= maxTypes, "字典类型超过配置上限");
        snapshot = Map.copyOf(next);
    }

    public synchronized void refreshAll() {
        Map<String, Map<String, String>> loaded = loader.loadAllDict();
        Assert.notNull(loaded, "字典加载器不能返回null快照");
        Assert.isTrue(loaded.size() <= maxTypes, "字典类型超过配置上限");
        Map<String, Map<String, String>> next = new LinkedHashMap<>();
        loaded.forEach((type, values) -> next.put(type, Map.copyOf(values)));
        snapshot = Map.copyOf(next);
    }

    public Map<String, Map<String, String>> snapshot() { return snapshot; }
}
