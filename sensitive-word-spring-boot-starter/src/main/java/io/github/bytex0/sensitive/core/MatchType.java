package io.github.bytex0.sensitive.core;

/**
 * 匹配模式(MatchType)在同一起点选择最短或最长词，结果之间不重叠。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public enum MatchType {

    /**
     * 选择同一起点最短的词，保留原默认行为。
     */
    MIN_MATCH,

    /**
     * 选择同一起点最长的词。
     */
    MAX_MATCH
}
