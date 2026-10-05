package io.github.bytex0.dict;

import java.util.Map;

/**
 * 字典来源(DictLoader)应用扩展接口
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
public interface DictLoader {

    /**
     * 加载单个字典，不存在时可返回空映射或 null，失败应抛出异常。
     *
     * @param type 字典类型
     * @return 编码到文本的映射
     */
    Map<String, String> loadDict(String type);

    /**
     * 加载完整字典快照，调用者负责复制，不能返回 null 快照。
     *
     * @return 类型到字典的映射
     */
    Map<String, Map<String, String>> loadAllDict();
}
