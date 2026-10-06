package io.github.bytex0.sensitive.util;

import io.github.bytex0.sensitive.core.DfaSensitiveWordFilter;
import io.github.bytex0.sensitive.core.MatchType;
import io.github.bytex0.sensitive.core.SensitiveWordFilter;
import io.github.bytex0.sensitive.core.SensitiveWordResult;

import java.util.List;
import java.util.Set;

/**
 * 快捷工具(SensitiveWordUtil)保留原快捷操作，改用注入实例，不保存跨容器静态词库。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public final class SensitiveWordUtil {

    /**
     * 当前工具的过滤器引用，切换不影响其他工具或业务服务。
     */
    private volatile SensitiveWordFilter filter;

    /**
     * 独立工具默认使用空词库。
     */
    public SensitiveWordUtil() {
        this(new DfaSensitiveWordFilter());
    }

    /**
     * 绑定已有过滤器。
     *
     * @param filter 过滤器
     */
    public SensitiveWordUtil(SensitiveWordFilter filter) {
        setFilter(filter);
    }

    /**
     * 获取当前过滤器，不自动叠加业务服务白名单。
     *
     * @return 当前过滤器
     */
    public SensitiveWordFilter getFilter() {
        return filter;
    }

    /**
     * 替换当前实例的过滤器，null 重置为空词库。
     *
     * @param filter 新过滤器
     */
    public void setFilter(SensitiveWordFilter filter) {
        this.filter = filter == null ? new DfaSensitiveWordFilter() : filter;
    }

    /**
     * 添加无分类词。
     *
     * @param word 词条
     */
    public void addWord(String word) {
        filter.addWord(word);
    }

    /**
     * 添加分类词。
     *
     * @param word 词条
     * @param category 分类
     */
    public void addWord(String word, String category) {
        filter.addWord(word, category);
    }

    /**
     * 批量添加词。
     *
     * @param words 词集合
     */
    public void addWords(Set<String> words) {
        filter.addWords(words);
    }

    /**
     * 最小匹配检测。
     *
     * @param text 原文
     * @return 是否命中
     */
    public boolean contains(String text) {
        return filter.contains(text);
    }

    /**
     * 指定模式检测。
     *
     * @param text 原文
     * @param matchType 策略
     * @return 是否命中
     */
    public boolean contains(String text, MatchType matchType) {
        return filter.contains(text, matchType);
    }

    /**
     * 首个最小匹配。
     *
     * @param text 原文
     * @return 结果或 null
     */
    public SensitiveWordResult findFirst(String text) {
        return filter.findFirst(text);
    }

    /**
     * 全部最小匹配。
     *
     * @param text 原文
     * @return 结果列表
     */
    public List<SensitiveWordResult> findAll(String text) {
        return filter.findAll(text);
    }

    /**
     * 固定字符替换。
     *
     * @param text 原文
     * @param replacement 字符
     * @return 结果
     */
    public String replace(String text, char replacement) {
        return filter.replace(text, replacement);
    }

    /**
     * 固定字符串替换。
     *
     * @param text 原文
     * @param replacement 替换串
     * @return 结果
     */
    public String replace(String text, String replacement) {
        return filter.replace(text, replacement);
    }

    /**
     * 包裹最小匹配原文。
     *
     * @param text 原文
     * @param startTag 起始标签
     * @param endTag 结束标签
     * @return 结果
     */
    public String highlight(String text, String startTag, String endTag) {
        return filter.highlight(text, startTag, endTag);
    }
}
