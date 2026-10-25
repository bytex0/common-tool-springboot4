package io.github.bytex0.nlp;

import io.github.bytex0.config.CommonNlpAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 词典匹配、搜索预算及真实 HanLP 引擎功能测试，不依赖外部模型下载。
 *
 * @author bytex0
 * @since 2026-10-06 18:17:11
 */
class NlpTest {

    /**
     * 包含多个句子的固定中文测试语料。
     */
    private static final String TEXT = "自然语言处理可以帮助计算机理解中文。"
            + "中文分词是自然语言处理的重要基础。机器学习促进自然语言处理发展。";

    /**
     * 验证双向算法、超过四字的词、组合数量和实例隔离。
     */
    @Test
    void dictionarySupportsAllModesAndLongWords() {
        DictionarySegmenter segmenter = new DictionarySegmenter();
        segmenter.initDictionary(List.of("研究", "研究生", "生命", "命", "起源", "自然语言处理系统"));
        assertThat(segmenter.forwardMaximumMatching("研究生命起源")).containsExactly("研究生", "命", "起源");
        assertThat(segmenter.backwardMaximumMatching("研究生命起源")).containsExactly("研究", "生命", "起源");
        assertThat(segmenter.bidirectionalMaximumMatching("研究生命起源")).containsExactly("研究", "生命", "起源");
        assertThat(segmenter.forwardMaximumMatching("自然语言处理系统")).containsExactly("自然语言处理系统");
        assertThat(segmenter.getAllPossibleSegmentations("研究生命")).hasSize(5);
        assertThat(segmenter.evaluateSegmentation(List.of("研究", "生命"))).isGreaterThan(0);
        assertThat(new DictionarySegmenter().forwardMaximumMatching("研究")).containsExactly("研", "究");
    }

    /**
     * 验证补充平面字符不被拆开，深输入不依赖递归栈，超预算不返回部分结果。
     */
    @Test
    void dictionaryHandlesUnicodeAndBudgets() {
        String supplementary = "\uD840\uDC00";
        DictionarySegmenter segmenter = new DictionarySegmenter();
        assertThat(segmenter.forwardMaximumMatching("甲" + supplementary + "乙"))
                .containsExactly("甲", supplementary, "乙");
        assertThat(segmenter.getAllPossibleSegmentations("a".repeat(5000)).iterator().next()).hasSize(5000);
        DictionarySegmenter limited = new DictionarySegmenter(1, 10);
        limited.addWord("aa");
        assertThatThrownBy(() -> limited.getAllPossibleSegmentations("aa")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DictionarySegmenter(10, 1).getAllPossibleSegmentations("ab"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 验证 HanLP 所有主要提取与转换入口使用真实内置词典运行。
     */
    @Test
    void hanlpPerformsSegmentationExtractionAndConversion() {
        HanlpUtil hanlp = new HanlpUtil();
        assertThat(hanlp.standardSegment(TEXT)).contains("中文");
        assertThat(hanlp.standardSegmentWithNature(TEXT)).isNotEmpty();
        assertThat(hanlp.indexSegment(TEXT)).isNotEmpty();
        assertThat(hanlp.nlpSegment(TEXT)).isNotEmpty();
        assertThat(hanlp.extractKeywords(TEXT, 2)).isNotEmpty().hasSizeLessThanOrEqualTo(2);
        assertThat(hanlp.extractSummary(TEXT, 1)).hasSize(1);
        assertThat(hanlp.extractPhrase(TEXT, 2)).isNotEmpty().hasSizeLessThanOrEqualTo(2);
        assertThat(hanlp.suggest("自然语言处理", 2)).contains("自然语言处理");
        assertThat(hanlp.suggest(List.of("自然语言处理", "数据库事务"), "自然语言处理", 1))
                .containsExactly("自然语言处理");
        assertThat(hanlp.convertToSimplifiedChinese("繁體中文")).isEqualTo("繁体中文");
        assertThat(hanlp.convertToTraditionalChinese("简体中文")).isEqualTo("簡體中文");
        List<String> mutable = hanlp.standardSegment("中文");
        mutable.add("追加词");
        assertThat(hanlp.standardSegment("中文")).doesNotContain("追加词");
    }

    /**
     * 验证自定义词库只影响当前实例，删除后恢复基础字典。
     */
    @Test
    void hanlpCustomWordsAreIsolated() {
        HanlpUtil first = new HanlpUtil();
        HanlpUtil second = new HanlpUtil();
        String word = "字节零联合测试词";
        first.addCustomDictionary(word, "nz", 10000);
        assertThat(first.isCustomWord(word)).isTrue();
        assertThat(second.isCustomWord(word)).isFalse();
        assertThat(first.standardSegment(word)).containsExactly(word);
        first.addCustomDictionary(word, "n", 1);
        assertThat(first.standardSegmentWithNature(word).getFirst().nature.toString()).isEqualTo("nz");
        first.removeCustomDictionary(word);
        assertThat(first.isCustomWord(word)).isFalse();
        first.addCustomDictionary(word);
        assertThat(first.isCustomWord(word)).isTrue();
        assertThatThrownBy(() -> first.addCustomDictionary(word, "nz", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证引擎显式启用、用户覆盖及缺少可选依赖时仍有普通词典。
     */
    @Test
    void configurationSeparatesOptionalEngine() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonNlpAutoConfiguration.class));
        runner.run(context -> {
            assertThat(context).hasSingleBean(DictionarySegmenter.class);
            assertThat(context).doesNotHaveBean(HanlpUtil.class);
        });
        runner.withPropertyValues("common-tool.nlp.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DictionarySegmenter.class));
        runner.withPropertyValues("common-tool.nlp.hanlp-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(HanlpUtil.class));
        runner.withClassLoader(new FilteredClassLoader("com.hankcs"))
                .withPropertyValues("common-tool.nlp.hanlp-enabled=true").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DictionarySegmenter.class);
                });
        DictionarySegmenter custom = new DictionarySegmenter();
        runner.withBean(DictionarySegmenter.class, () -> custom)
                .run(context -> assertThat(context.getBean(DictionarySegmenter.class)).isSameAs(custom));
    }
}
