package io.github.bytex0.sensitive.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 原匹配结果(SensitiveWordResult)保留词库词条、分类和原文 UTF-16 闭区间。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveWordResult implements Serializable {

    /**
     * 序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 注册时的词库词条，不是归一化后或替换后的文本。
     */
    private String word;

    /**
     * 原文 UTF-16 起始下标，包含该位置。
     */
    private int startIndex;

    /**
     * 原文 UTF-16 结束下标，包含该位置。
     */
    private int endIndex;

    /**
     * 注册时的分类，可为空。
     */
    private String category;
}
