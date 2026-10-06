package io.github.bytex0.sensitive.core;

/**
 * 文本处理(HandleType)定义命中敏感词后的业务动作。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public enum HandleType {

    /**
     * 替换命中区间。
     */
    REPLACE,

    /**
     * 抛出包含结构化结果的业务异常。
     */
    EXCEPTION,

    /**
     * 在命中原文两侧添加指定标签，不自动执行 HTML 转义。
     */
    HIGHLIGHT,

    /**
     * 执行检测但保留原文本，不记录敏感正文。
     */
    DETECT_ONLY
}
