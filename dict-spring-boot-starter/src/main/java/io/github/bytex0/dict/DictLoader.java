package io.github.bytex0.dict;

import java.util.Map;

/**
 * 字典来源(DictLoader)应用扩展接口
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
public interface DictLoader {
    Map<String, String> loadDict(String type);
    Map<String, Map<String, String>> loadAllDict();
}
