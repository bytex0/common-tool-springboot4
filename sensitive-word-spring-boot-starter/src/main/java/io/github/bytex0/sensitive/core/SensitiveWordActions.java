package io.github.bytex0.sensitive.core;

import java.util.List;
import java.util.Set;

/**
 * 文本操作(SensitiveWordActions)共享管理与处理契约，区分过滤器 Bean 和带白名单的业务服务。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public interface SensitiveWordActions {

    /**
     * 添加无分类词条，空白词忽略。
     *
     * @param word 词条
     */
    void addWord(String word);

    /**
     * 添加带分类词条，等价词已存在时保留首次注册内容。
     *
     * @param word 词条
     * @param category 可选分类
     */
    void addWord(String word, String category);

    /**
     * 批量添加无分类词条。
     *
     * @param words 词集合，null 无操作
     */
    void addWords(Set<String> words);

    /**
     * 原子添加一批词条，失败不发布半批数据。
     *
     * @param words 词集合
     * @param category 分类
     */
    void addWords(Set<String> words, String category);

    /**
     * 按当前归一化规则删除词条。
     *
     * @param word 词条
     */
    void removeWord(String word);

    /**
     * 原子删除一批词条。
     *
     * @param words 词集合
     */
    void removeWords(Set<String> words);

    /**
     * 清空词库，保留匹配选项。
     */
    void clear();

    /**
     * 获取当前归一化后的唯一词数。
     *
     * @return 词数
     */
    int size();

    /**
     * 按最小匹配判断是否命中。
     *
     * @param text 原文，可为空
     * @return 是否命中
     */
    boolean contains(String text);

    /**
     * 按指定策略判断是否命中。
     *
     * @param text 原文
     * @param matchType 非空策略
     * @return 是否命中
     */
    boolean contains(String text, MatchType matchType);

    /**
     * 查询首个最小匹配。
     *
     * @param text 原文
     * @return 结果，未命中为 null
     */
    SensitiveWordResult findFirst(String text);

    /**
     * 查询首个指定模式匹配。
     *
     * @param text 原文
     * @param matchType 策略
     * @return 结果或 null
     */
    SensitiveWordResult findFirst(String text, MatchType matchType);

    /**
     * 查询全部非重叠最小匹配。
     *
     * @param text 原文
     * @return 原文闭区间列表
     */
    List<SensitiveWordResult> findAll(String text);

    /**
     * 查询全部非重叠匹配。
     *
     * @param text 原文
     * @param matchType 策略
     * @return 结果列表
     */
    List<SensitiveWordResult> findAll(String text, MatchType matchType);

    /**
     * 按原 UTF-16 区间长度替换字符，默认最小匹配。
     *
     * @param text 原文
     * @param replacement 替换字符
     * @return 替换结果
     */
    String replace(String text, char replacement);

    /**
     * 按指定策略进行字符替换。
     *
     * @param text 原文
     * @param replacement 替换字符
     * @param matchType 策略
     * @return 结果
     */
    String replace(String text, char replacement, MatchType matchType);

    /**
     * 每个最小匹配替换为一个固定字符串。
     *
     * @param text 原文
     * @param replacement 非空替换串，空串表示删除
     * @return 结果
     */
    String replace(String text, String replacement);

    /**
     * 包裹最小匹配原文，标签可信性及 HTML 转义由使用方负责。
     *
     * @param text 原文
     * @param startTag 起始标签
     * @param endTag 结束标签
     * @return 包裹结果
     */
    String highlight(String text, String startTag, String endTag);

    /**
     * 包裹指定模式的匹配原文。
     *
     * @param text 原文
     * @param startTag 起始标签
     * @param endTag 结束标签
     * @param matchType 策略
     * @return 包裹结果
     */
    String highlight(String text, String startTag, String endTag, MatchType matchType);
}
