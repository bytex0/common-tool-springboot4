package io.github.bytex0.sensitive;

import io.github.bytex0.sensitive.core.DfaSensitiveWordFilter;
import io.github.bytex0.sensitive.core.MatchType;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.util.List;
import java.util.Set;

/**
 * 基于 Aho-Corasick 的原子词库服务，查询和替换共享同一白名单语义。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
public class SensitiveWordService {

    /**
     * 与原包入口共享的实际业务组件。
     */
    private final SensitiveWordOperations operations;

    /**
     * 保留当前独立构造器，构造完成后词库可用。
     *
     * @param properties 配置
     */
    public SensitiveWordService(SensitiveWordProperties properties) {
        this(properties, new DefaultResourceLoader());
    }

    /**
     * 使用指定资源加载器构造独立服务。
     *
     * @param properties 配置
     * @param resources 资源加载器
     */
    public SensitiveWordService(SensitiveWordProperties properties, ResourceLoader resources) {
        this(new SensitiveWordOperations(new DfaSensitiveWordFilter(properties), properties, resources));
    }

    /**
     * 包装已配置的业务组件，初始化具有幂等性。
     *
     * @param operations 业务组件
     */
    private SensitiveWordService(SensitiveWordOperations operations) {
        this.operations = operations;
        operations.afterPropertiesSet();
    }

    /**
     * 创建共享门面，原包服务和根包服务使用同一词库/白名单。
     *
     * @param operations 业务组件
     * @return 当前接口门面
     */
    public static SensitiveWordService from(SensitiveWordOperations operations) {
        return new SensitiveWordService(operations);
    }

    /**
     * 原子替换内置词库。
     *
     * @param words 词条
     */
    public void replaceWords(Set<String> words) {
        operations.replaceWords(words);
    }

    /**
     * 添加无分类词。
     *
     * @param word 词条
     */
    public void addWord(String word) {
        operations.addWord(word);
    }

    /**
     * 删除词条，遵守当前归一化规则。
     *
     * @param word 词条
     */
    public void removeWord(String word) {
        operations.removeWord(word);
    }

    /**
     * 获取词数。
     *
     * @return 唯一词数
     */
    public int size() {
        return operations.size();
    }

    /**
     * 检测是否命中，白名单范围不参与检测。
     *
     * @param text 原文
     * @return 是否命中
     */
    public boolean contains(String text) {
        return operations.contains(text);
    }

    /**
     * 保留根包结果的原文文本和右开区间，不混用旧结果的闭区间协议。
     *
     * @param text 原文
     * @param longest 是否最长匹配
     * @return 根包结果
     */
    public List<Match> findAll(String text, boolean longest) {
        return operations.findAll(text, longest ? MatchType.MAX_MATCH : MatchType.MIN_MATCH).stream()
                .map(match -> new Match(text.substring(match.getStartIndex(), match.getEndIndex() + 1),
                        match.getStartIndex(), match.getEndIndex() + 1)).toList();
    }

    /**
     * 保留根包按最长匹配、Unicode 码点数输出星号的行为。
     *
     * @param text 原文
     * @return 替换结果
     */
    public String replace(String text) {
        List<Match> matches = findAll(text, true);
        if (matches.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text);
        for (Match match : matches.reversed()) {
            result.replace(match.startIndex(), match.endIndex(),
                    "*".repeat(text.codePointCount(match.startIndex(), match.endIndex())));
        }
        return result.toString();
    }

    /**
     * 保留根包拒绝异常类型，不在异常消息内输出正文。
     *
     * @param text 原文
     */
    public void reject(String text) {
        if (contains(text)) {
            throw new IllegalArgumentException("Text contains prohibited content");
        }
    }

    /**
     * 原文 UTF-16 索引，结束位置不包含在结果中。
     *
     * @param word 匹配的原文
     * @param startIndex 开始位置
     * @param endIndex 结束位置
     * @author bytex0
     * @since 2026-10-05 19:21:42
     */
    public record Match(
            /**
             * 匹配原文。
             */
            String word,

            /**
             * 包含的 UTF-16 起点。
             */
            int startIndex,

            /**
             * 不包含的 UTF-16 终点。
             */
            int endIndex) {
    }
}
