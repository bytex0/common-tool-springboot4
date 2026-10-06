package io.github.bytex0.sensitive;

import io.github.bytex0.sensitive.core.DfaSensitiveWordFilter;
import io.github.bytex0.sensitive.core.MatchType;
import io.github.bytex0.sensitive.core.SensitiveWordResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 原过滤接口(LegacyFilterTest)验证词条分类、闭区间和配置变化后的实际匹配。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
class LegacyFilterTest {

    /**
     * 最小与最大匹配使用原始 UTF-16 闭区间，不吞掉词前空白。
     */
    @Test
    void preservesCategorizedDictionaryWordsAndInclusiveOriginalOffsets() {
        DfaSensitiveWordFilter filter = new DfaSensitiveWordFilter();
        filter.addWords(Set.of("bad", "badly"), "moderation");
        SensitiveWordResult first = filter.findFirst("😀 B A Dly", MatchType.MIN_MATCH);
        assertThat(first).isEqualTo(new SensitiveWordResult("bad", 3, 7, "moderation"));
        assertThat(filter.findFirst("😀 B A Dly", MatchType.MAX_MATCH).getWord()).isEqualTo("badly");
        assertThat(filter.replace("😀 B A Dly", '*', MatchType.MIN_MATCH)).isEqualTo("😀 *****ly");
        assertThat(filter.highlight(" BAD", "<b>", "</b>")).isEqualTo(" <b>BAD</b>");
        filter.addWord("ος");
        assertThat(filter.contains("ΟΣ")).isTrue();
    }

    /**
     * 配置修改重新编译同一个词库，不能留下旧归一化状态。
     */
    @Test
    void rebuildsWhenNormalizationRulesChange() {
        DfaSensitiveWordFilter filter = new DfaSensitiveWordFilter(false);
        filter.addWord("BAD");
        assertThat(filter.contains("bad")).isFalse();
        filter.setIgnoreCase(true);
        assertThat(filter.contains("bad")).isTrue();
        filter.setSkipChars(Set.of('-'));
        assertThat(filter.contains("b-a-d")).isTrue();
        filter.removeWord("bad");
        assertThat(filter.size()).isZero();
        filter.addWords(Set.of("BAD", "bad"));
        assertThat(filter.size()).isEqualTo(1);
        filter.setIgnoreCase(false);
        assertThat(filter.size()).isEqualTo(1);
    }
}
