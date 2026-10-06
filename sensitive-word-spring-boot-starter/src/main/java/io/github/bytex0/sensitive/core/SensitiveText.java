package io.github.bytex0.sensitive.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 区间改写(SensitiveText)只根据已取得的原文闭区间处理，避免二次查询跨越词库版本。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
final class SensitiveText {

    /**
     * 纯函数工具不创建实例。
     */
    private SensitiveText() {
    }

    /**
     * 从后往前改写非重叠区间，替换字符串不作为正则表达式。
     *
     * @param text 原文
     * @param matches 同次查询的结果
     * @param replacement 每个匹配的目标文本
     * @return 改写结果，null 原文返回 null
     */
    static String rewrite(String text, List<SensitiveWordResult> matches, Function<SensitiveWordResult, String> replacement) {
        if (text == null || matches.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text);
        for (SensitiveWordResult match : matches.reversed()) {
            result.replace(match.getStartIndex(), match.getEndIndex() + 1, Objects.requireNonNull(replacement.apply(match)));
        }
        return result.toString();
    }
}
