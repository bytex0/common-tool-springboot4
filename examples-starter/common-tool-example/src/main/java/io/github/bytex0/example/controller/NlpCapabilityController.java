package io.github.bytex0.example.controller;

import com.hankcs.hanlp.seg.common.Term;
import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.nlp.DictionarySegmenter;
import io.github.bytex0.nlp.HanlpUtil;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 固定语料 NLP 验证接口，调用 Starter 自动装配的字典和 HanLP 工具。
 *
 * @author bytex0
 * @since 2026-10-06 18:22:06
 */
@RestController
public class NlpCapabilityController {

    /**
     * 不含业务信息的固定测试语料。
     */
    private static final String TEXT = "自然语言处理可以帮助计算机理解中文。"
            + "中文分词是自然语言处理的重要基础。机器学习促进自然语言处理发展。";

    /**
     * 自动配置的实例字典。
     */
    private final DictionarySegmenter dictionary;

    /**
     * 自动配置的 HanLP 工具。
     */
    private final HanlpUtil hanlp;

    /**
     * 注入组件并初始化示例独有词库，不修改 HanLP 全局词典。
     *
     * @param dictionary 实例字典
     * @param hanlp HanLP 工具
     */
    public NlpCapabilityController(DictionarySegmenter dictionary, HanlpUtil hanlp) {
        this.dictionary = dictionary;
        this.hanlp = hanlp;
        dictionary.initDictionary(List.of("研究", "研究生", "生命", "命", "起源", "自然语言处理系统"));
    }

    /**
     * 验证双向模式、长词和组合枚举。
     *
     * @return 各模式结果
     */
    @GetMapping("/api/common/nlp/dictionary")
    public ApiResponse<Map<String, Object>> dictionary() {
        return ApiResponse.ok(Map.of(
                "forward", dictionary.forwardMaximumMatching("研究生命起源"),
                "backward", dictionary.backwardMaximumMatching("研究生命起源"),
                "bidirectional", dictionary.bidirectionalMaximumMatching("研究生命起源"),
                "longWord", dictionary.forwardMaximumMatching("自然语言处理系统"),
                "combinations", dictionary.getAllPossibleSegmentations("研究生命").size(),
                "score", dictionary.evaluateSegmentation(List.of("研究", "生命"))));
    }

    /**
     * 验证 HanLP 内置词典和主要算法，SDK Term 显式转换为简单数据。
     *
     * @return 分词、提取、推荐和转换结果
     */
    @GetMapping("/api/common/nlp/hanlp")
    public ApiResponse<Map<String, Object>> hanlp() {
        return ApiResponse.ok(Map.of(
                "words", hanlp.standardSegment(TEXT),
                "terms", terms(hanlp.standardSegmentWithNature(TEXT)),
                "indexWords", hanlp.indexSegment(TEXT),
                "nlpTerms", terms(hanlp.nlpSegment(TEXT)),
                "keywords", hanlp.extractKeywords(TEXT, 2),
                "summary", hanlp.extractSummary(TEXT, 1),
                "phrases", hanlp.extractPhrase(TEXT, 2),
                "suggestions", hanlp.suggest(List.of("自然语言处理", "数据库事务"), "自然语言处理", 1),
                "simplified", hanlp.convertToSimplifiedChinese("繁體中文"),
                "traditional", hanlp.convertToTraditionalChinese("简体中文")));
    }

    /**
     * 使用随机测试词验证新增和删除，退出前清理本次词语。
     *
     * @return 词库状态与分词结果
     */
    @PostMapping("/api/common/nlp/custom")
    public ApiResponse<Map<String, Object>> custom() {
        String word = "字节零" + UUID.randomUUID().toString().replace("-", "");
        try {
            hanlp.addCustomDictionary(word, "nz", 10000);
            boolean during = hanlp.isCustomWord(word);
            List<String> words = hanlp.standardSegment(word);
            hanlp.removeCustomDictionary(word);
            return ApiResponse.ok(Map.of("word", word, "during", during,
                    "after", hanlp.isCustomWord(word), "words", words));
        } finally {
            hanlp.removeCustomDictionary(word);
        }
    }

    /**
     * 将 SDK 内部词性对象转换为字符串。
     *
     * @param terms SDK 分词结果
     * @return 可 JSON 序列化的词语与词性
     */
    private List<Map<String, String>> terms(List<Term> terms) {
        return terms.stream().map(term -> Map.of(
                "word", term.word, "nature", term.nature == null ? "" : term.nature.toString())).toList();
    }
}
