package io.github.bytex0.nlp;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.corpus.occurrence.Occurrence;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.DynamicCustomDictionary;
import com.hankcs.hanlp.dictionary.stopword.CoreStopWordDictionary;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.suggest.Suggester;
import com.hankcs.hanlp.summary.TextRankKeyword;
import com.hankcs.hanlp.summary.TextRankSentence;
import com.hankcs.hanlp.utility.SentencesUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * HanLP 成品能力封装，实例自定义词典以不可变快照发布，不修改 SDK 全局词库。
 *
 * @author bytex0
 * @since 2026-10-06 18:14:43
 */
public class HanlpUtil {

    /**
     * 默认新增词的词性及频率，保持 HanLP 默认新增词语义。
     */
    private static final String DEFAULT_ATTRIBUTE = "nz 1";

    /**
     * 当前实例的自定义词库快照，快照发布后不再修改内部字典。
     */
    private final AtomicReference<Lexicon> lexicon = new AtomicReference<>(build(Map.of()));

    /**
     * 标准分词，仅返回词语。
     *
     * @param text 待分词文本，null 或空文本返回空列表
     * @return 词语列表
     */
    public List<String> standardSegment(String text) {
        return standardSegmentWithNature(text).stream().map(term -> term.word)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 标准分词并返回 HanLP 词性模型。
     *
     * @param text 待分词文本
     * @return 独立分词结果，不返回内部词典
     */
    public List<Term> standardSegmentWithNature(String text) {
        return empty(text) ? new ArrayList<>() : segment(lexicon.get()).seg(text);
    }

    /**
     * 索引模式分词，保留长词及内部索引词。
     *
     * @param text 待分词文本
     * @return 索引词列表
     */
    public List<String> indexSegment(String text) {
        if (empty(text)) {
            return new ArrayList<>();
        }
        return segment(lexicon.get()).enableIndexMode(true).seg(text).stream().map(term -> term.word)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 启用人名、地名和机构名识别。
     *
     * @param text 待分词文本
     * @return 分词及词性结果
     */
    public List<Term> nlpSegment(String text) {
        if (empty(text)) {
            return new ArrayList<>();
        }
        return segment(lexicon.get()).enableNameRecognize(true).enablePlaceRecognize(true)
                .enableOrganizationRecognize(true).seg(text);
    }

    /**
     * 使用 HanLP TextRank 提取关键词，使用当前实例分词器。
     *
     * @param text 文本
     * @param keywordCount 非负数量，0 返回空列表
     * @return 关键词列表
     */
    public List<String> extractKeywords(String text, int keywordCount) {
        checkCount(keywordCount);
        if (empty(text) || keywordCount == 0) {
            return new ArrayList<>();
        }
        return new TextRankKeyword(segment(lexicon.get())).getKeywords(text, Math.min(keywordCount, text.length()));
    }

    /**
     * 使用 HanLP 断句和 TextRank 句子排序提取摘要，分词使用同一实例词库快照。
     *
     * @param text 文本
     * @param sentenceCount 非负句子数
     * @return 按重要性排序的摘要句子
     */
    public List<String> extractSummary(String text, int sentenceCount) {
        checkCount(sentenceCount);
        if (empty(text) || sentenceCount == 0) {
            return new ArrayList<>();
        }
        List<String> sentences = SentencesUtil.toSentenceList(text);
        if (sentences.isEmpty()) {
            return new ArrayList<>();
        }
        Segment segment = segment(lexicon.get());
        List<List<String>> documents = new ArrayList<>();
        for (String sentence : sentences) {
            List<Term> terms = segment.seg(sentence);
            CoreStopWordDictionary.apply(terms);
            documents.add(terms.stream().map(term -> term.word).toList());
        }
        int[] indexes = new TextRankSentence(documents).getTopSentence(Math.min(sentenceCount, sentences.size()));
        List<String> result = new ArrayList<>();
        for (int index : indexes) {
            result.add(sentences.get(index));
        }
        return result;
    }

    /**
     * 保留原单文本推荐入口，候选语料为该文本本身。
     *
     * @param text 候选及查询文本
     * @param size 非负结果数
     * @return 推荐结果
     */
    public List<String> suggest(String text, int size) {
        checkCount(size);
        return empty(text) ? new ArrayList<>() : suggest(List.of(text), text, size);
    }

    /**
     * 使用显式候选集合进行真正的相似句推荐，不保存跨调用语料。
     *
     * @param candidates 非空候选集合
     * @param query 查询文本
     * @param size 非负结果数量
     * @return 推荐结果
     */
    public List<String> suggest(Collection<String> candidates, String query, int size) {
        Objects.requireNonNull(candidates, "candidates");
        checkCount(size);
        if (empty(query) || size == 0 || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        Suggester suggester = new Suggester();
        for (String candidate : candidates) {
            if (!empty(candidate)) {
                suggester.addSentence(candidate);
            }
        }
        return suggester.suggest(query, Math.min(size, candidates.size()));
    }

    /**
     * 新增当前实例的自定义词，已存在时保持原属性，失败时保留原快照。
     *
     * @param word 非空词语
     * @param nature 单个词性标识，不允许包含空白
     * @param frequency 正整数词频
     */
    public void addCustomDictionary(String word, String nature, int frequency) {
        if (nature == null || nature.isBlank() || nature.chars().anyMatch(Character::isWhitespace) || frequency <= 0) {
            throw new IllegalArgumentException("Nature and positive frequency are required");
        }
        updateWord(word, nature + " " + frequency);
    }

    /**
     * 使用默认 nz 词性和频率 1 添加词语。
     *
     * @param word 非空词语
     */
    public void addCustomDictionary(String word) {
        updateWord(word, DEFAULT_ATTRIBUTE);
    }

    /**
     * 删除本实例追加的词或覆盖项，内置基础词典保持可用。
     *
     * @param word 非空词语
     */
    public void removeCustomDictionary(String word) {
        updateWord(word, null);
    }

    /**
     * 查询当前实例词库是否包含词语。
     *
     * @param word 词语
     * @return 是否存在
     */
    public boolean isCustomWord(String word) {
        return !empty(word) && lexicon.get().dictionary.contains(word);
    }

    /**
     * 繁体转简体，不修改实例词典。
     *
     * @param text 文本，为空原样返回
     * @return 简体文本
     */
    public String convertToSimplifiedChinese(String text) {
        return empty(text) ? text : HanLP.convertToSimplifiedChinese(text);
    }

    /**
     * 简体转繁体，不修改实例词典。
     *
     * @param text 文本，为空原样返回
     * @return 繁体文本
     */
    public String convertToTraditionalChinese(String text) {
        return empty(text) ? text : HanLP.convertToTraditionalChinese(text);
    }

    /**
     * 使用 HanLP 互信息和左右熵提取短语，保留停用词及时间/外文词过滤规则。
     *
     * @param text 文本
     * @param size 非负短语数量
     * @return 短语列表
     */
    public List<String> extractPhrase(String text, int size) {
        checkCount(size);
        if (empty(text) || size == 0) {
            return new ArrayList<>();
        }
        Occurrence occurrence = new Occurrence();
        for (List<Term> sentence : segment(lexicon.get()).seg2sentence(text)) {
            sentence.removeIf(term -> !CoreStopWordDictionary.FILTER.shouldInclude(term)
                    || term.nature == Nature.t || term.nature == Nature.nx);
            occurrence.addAll(sentence);
        }
        occurrence.compute();
        return occurrence.getPhraseByScore().stream().limit(size).map(pair -> pair.first + pair.second)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 按写时复制发布新词库，不持锁执行词典资源加载。
     *
     * @param word 非空词语
     * @param attribute 词性频率，null 表示删除实例覆盖项
     */
    private void updateWord(String word, String attribute) {
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("Custom word must not be blank");
        }
        for (;;) {
            Lexicon previous = lexicon.get();
            if (attribute != null && previous.dictionary.contains(word)) {
                return;
            }
            Map<String, String> entries = new HashMap<>(previous.entries);
            if (attribute == null) {
                if (entries.remove(word) == null) {
                    return;
                }
            } else {
                entries.put(word, attribute);
            }
            Lexicon updated = build(Map.copyOf(entries));
            if (lexicon.compareAndSet(previous, updated)) {
                return;
            }
        }
    }

    /**
     * 独立加载 HanLP 自定义基础词典并应用实例覆盖项。
     *
     * @param entries 不可变覆盖项
     * @return 可安全发布的只读快照
     */
    private Lexicon build(Map<String, String> entries) {
        DynamicCustomDictionary dictionary = new DynamicCustomDictionary();
        entries.forEach((word, attribute) -> {
            if (!dictionary.insert(word, attribute)) {
                throw new IllegalArgumentException("Cannot add custom dictionary word");
            }
        });
        return new Lexicon(entries, dictionary);
    }

    /**
     * 创建一次调用拥有的分词器，避免共享可变 Segment 配置。
     *
     * @param snapshot 当前词库快照
     * @return 新分词器
     */
    private Segment segment(Lexicon snapshot) {
        return HanLP.newSegment().enableCustomDictionary(snapshot.dictionary);
    }

    /**
     * 判断空文本。
     *
     * @param text 文本
     * @return 是否为 null 或空字符串
     */
    private boolean empty(String text) {
        return text == null || text.isEmpty();
    }

    /**
     * 验证请求数量非负。
     *
     * @param count 请求数量
     */
    private void checkCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Result count must not be negative");
        }
    }

    /**
     * 保留原演示入口，仅使用本地实例。
     *
     * @param args 可选待分词文本
     */
    public static void main(String[] args) {
        System.out.println(new HanlpUtil().standardSegment(args.length == 0 ? "自然语言处理" : args[0]));
    }

    /**
     * 实例覆盖项和不再修改的 HanLP 字典。
     *
     * @param entries 实例覆盖项
     * @param dictionary 只读发布字典
     * @author bytex0
     * @since 2026-10-06 18:14:43
     */
    private record Lexicon(
            /**
             * 不可变实例覆盖项。
             */
            Map<String, String> entries,

            /**
             * 发布后不再修改的字典。
             */
            DynamicCustomDictionary dictionary) {
    }
}
