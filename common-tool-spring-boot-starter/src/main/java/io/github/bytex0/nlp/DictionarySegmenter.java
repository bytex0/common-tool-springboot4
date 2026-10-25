package io.github.bytex0.nlp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 实例隔离的字典分词器，支持正向、逆向、双向匹配及有界全组合枚举。
 *
 * @author bytex0
 * @since 2026-10-06 18:11:36
 */
public class DictionarySegmenter {

    /**
     * 默认最多返回的分词组合数，超过限制明确失败，不返回截断集合。
     */
    private static final int DEFAULT_MAX_COMBINATIONS = 10000;

    /**
     * 默认组合结果包含的总词元上限，避免少量超长组合占用过多内存。
     */
    private static final long DEFAULT_MAX_OUTPUT_TOKENS = 1000000;

    /**
     * 保留原评分公式的平均词长归一化尺度，不再用于限制匹配词长。
     */
    private static final double SCORE_LENGTH_SCALE = 4.0;

    /**
     * 词典命中比例权重。
     */
    private static final double DICTIONARY_WEIGHT = 0.5;

    /**
     * 平均词长权重。
     */
    private static final double LENGTH_WEIGHT = 0.3;

    /**
     * 非单字比例权重。
     */
    private static final double MULTI_CHARACTER_WEIGHT = 0.2;

    /**
     * 可原子替换的不可变词库快照。
     */
    private final AtomicReference<Dictionary> dictionary = new AtomicReference<>(new Dictionary(Set.of(), 1));

    /**
     * 全组合数量上限。
     */
    private final int maxCombinations;

    /**
     * 全组合结果的总词元上限。
     */
    private final long maxOutputTokens;

    /**
     * 创建空词典及默认有界枚举器。
     */
    public DictionarySegmenter() {
        this(DEFAULT_MAX_COMBINATIONS, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    /**
     * 指定组合输出预算，正常单路径分词不受组合预算限制。
     *
     * @param maxCombinations 正数组合数量上限
     * @param maxOutputTokens 正数总输出词元上限
     */
    public DictionarySegmenter(int maxCombinations, long maxOutputTokens) {
        if (maxCombinations <= 0 || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("Segmentation budgets must be positive");
        }
        this.maxCombinations = maxCombinations;
        this.maxOutputTokens = maxOutputTokens;
    }

    /**
     * 原子替换当前实例词库，读取不会看到部分初始化状态。
     *
     * @param words 非空词集合，不能含 null 或空词
     */
    public void initDictionary(Collection<String> words) {
        Objects.requireNonNull(words, "words");
        Set<String> copy = new HashSet<>();
        int maxLength = 1;
        for (String word : words) {
            validateWord(word);
            copy.add(word);
            maxLength = Math.max(maxLength, word.codePointCount(0, word.length()));
        }
        dictionary.set(new Dictionary(Set.copyOf(copy), maxLength));
    }

    /**
     * 向当前实例添加词语，按 Unicode 码点计算最大词长。
     *
     * @param word 非空词语
     */
    public void addWord(String word) {
        validateWord(word);
        dictionary.updateAndGet(current -> {
            if (current.words.contains(word)) {
                return current;
            }
            Set<String> words = new HashSet<>(current.words);
            words.add(word);
            return new Dictionary(Set.copyOf(words),
                    Math.max(current.maxLength, word.codePointCount(0, word.length())));
        });
    }

    /**
     * 正向最大匹配，未登录词保留完整 Unicode 码点。
     *
     * @param text 文本，null 或空文本返回空列表
     * @return 独立分词列表
     */
    public List<String> forwardMaximumMatching(String text) {
        return maximumMatching(text, dictionary.get(), false);
    }

    /**
     * 逆向最大匹配，完成后一次性反转结果，避免头部插入的二次方开销。
     *
     * @param text 文本，null 或空文本返回空列表
     * @return 正序分词列表
     */
    public List<String> backwardMaximumMatching(String text) {
        return maximumMatching(text, dictionary.get(), true);
    }

    /**
     * 在同一词库快照下执行双向匹配，优先少词数，再优先少单字，平局保留原逆向优先语义。
     *
     * @param text 文本
     * @return 选中的分词结果
     */
    public List<String> bidirectionalMaximumMatching(String text) {
        Dictionary snapshot = dictionary.get();
        List<String> forward = maximumMatching(text, snapshot, false);
        List<String> backward = maximumMatching(text, snapshot, true);
        if (forward.size() != backward.size()) {
            return forward.size() < backward.size() ? forward : backward;
        }
        return countSingleWords(forward) < countSingleWords(backward) ? forward : backward;
    }

    /**
     * 按正向或逆向执行最大匹配，不读取其他快照。
     *
     * @param text 文本
     * @param snapshot 词库快照
     * @param reverse 是否逆向
     * @return 正序分词结果
     */
    private List<String> maximumMatching(String text, Dictionary snapshot, boolean reverse) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        int[] offsets = offsets(text);
        int length = offsets.length - 1;
        int position = reverse ? length : 0;
        while (reverse ? position > 0 : position < length) {
            int available = reverse ? position : length - position;
            int selectedLength = 1;
            for (int size = Math.min(snapshot.maxLength, available); size > 0; size--) {
                int from = reverse ? position - size : position;
                String candidate = text.substring(offsets[from], offsets[from + size]);
                if (snapshot.words.contains(candidate)) {
                    selectedLength = size;
                    break;
                }
            }
            int from = reverse ? position - selectedLength : position;
            result.add(text.substring(offsets[from], offsets[from + selectedLength]));
            position += reverse ? -selectedLength : selectedLength;
        }
        if (reverse) {
            Collections.reverse(result);
        }
        return result;
    }

    /**
     * 枚举所有词典匹配及单码点回退组合，使用迭代栈避免深递归溢出。
     *
     * @param text 文本，null 或空文本返回空集合
     * @return 组合集合，内部列表不可变
     * @throws IllegalStateException 组合数量或总词元超过配置预算
     */
    public Set<List<String>> getAllPossibleSegmentations(String text) {
        Set<List<String>> results = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return results;
        }
        Dictionary snapshot = dictionary.get();
        int[] offsets = offsets(text);
        Deque<SearchFrame> stack = new ArrayDeque<>();
        List<String> current = new ArrayList<>();
        stack.push(frame(text, offsets, 0, snapshot));
        long outputTokens = 0;
        while (!stack.isEmpty()) {
            SearchFrame frame = stack.peek();
            if (frame.next == frame.ends.size()) {
                stack.pop();
                if (!current.isEmpty()) {
                    current.removeLast();
                }
                continue;
            }
            int end = frame.ends.get(frame.next++);
            current.add(text.substring(offsets[frame.start], offsets[end]));
            if (end == offsets.length - 1) {
                outputTokens += current.size();
                if (results.size() >= maxCombinations || outputTokens > maxOutputTokens) {
                    throw new IllegalStateException("Segmentation result budget exceeded");
                }
                results.add(List.copyOf(current));
                current.removeLast();
            } else {
                stack.push(frame(text, offsets, end, snapshot));
            }
        }
        return results;
    }

    /**
     * 创建当前位置的候选列表，单码点回退不与词典单字重复。
     *
     * @param text 文本
     * @param offsets 码点到 UTF-16 偏移
     * @param start 起始码点位置
     * @param snapshot 词库快照
     * @return 搜索帧
     */
    private SearchFrame frame(String text, int[] offsets, int start, Dictionary snapshot) {
        List<Integer> ends = new ArrayList<>();
        int limit = start + Math.min(offsets.length - 1 - start, snapshot.maxLength);
        for (int end = start + 1; end <= limit; end++) {
            if (snapshot.words.contains(text.substring(offsets[start], offsets[end]))) {
                ends.add(end);
            }
        }
        if (!ends.contains(start + 1)) {
            ends.add(start + 1);
        }
        return new SearchFrame(start, ends);
    }

    /**
     * 使用原权重计算分词得分；计数按 Unicode 码点，不把代理对算成两个字符。
     *
     * @param words 分词结果，为空返回零分
     * @return 评分，原公式不保证结果限制在零到一之间
     */
    public double evaluateSegmentation(List<String> words) {
        if (words == null || words.isEmpty()) {
            return 0.0;
        }
        Dictionary snapshot = dictionary.get();
        double average = words.stream().mapToInt(word -> word.codePointCount(0, word.length())).average().orElse(0);
        double dictionaryRatio = (double) words.stream().filter(snapshot.words::contains).count() / words.size();
        double singleRatio = (double) countSingleWords(words) / words.size();
        return dictionaryRatio * DICTIONARY_WEIGHT + average / SCORE_LENGTH_SCALE * LENGTH_WEIGHT
                + (1 - singleRatio) * MULTI_CHARACTER_WEIGHT;
    }

    /**
     * 统计单码点词数量。
     *
     * @param words 词列表
     * @return 单码点词数量
     */
    private long countSingleWords(List<String> words) {
        return words.stream().filter(word -> word.codePointCount(0, word.length()) == 1).count();
    }

    /**
     * 建立码点边界，防止 substring 拆开 UTF-16 代理对。
     *
     * @param text 非空文本
     * @return 码点边界偏移数组
     */
    private int[] offsets(String text) {
        int[] result = new int[text.codePointCount(0, text.length()) + 1];
        for (int index = 0; index < result.length - 1; index++) {
            result[index + 1] = result[index] + Character.charCount(text.codePointAt(result[index]));
        }
        return result;
    }

    /**
     * 拒绝没有匹配意义的空词。
     *
     * @param word 词语
     */
    private void validateWord(String word) {
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("Dictionary word must not be empty");
        }
    }

    /**
     * 保留原演示入口，不修改任何全局词库。
     *
     * @param args 可选待分词文本
     */
    public static void main(String[] args) {
        DictionarySegmenter segmenter = new DictionarySegmenter();
        segmenter.initDictionary(List.of("自然语言", "处理"));
        System.out.println(segmenter.bidirectionalMaximumMatching(args.length == 0 ? "自然语言处理" : args[0]));
    }

    /**
     * 不可变词库及最大码点词长。
     *
     * @param words 不可变词集合
     * @param maxLength 最大码点词长
     * @author bytex0
     * @since 2026-10-06 18:11:36
     */
    private record Dictionary(
            /**
             * 不可变词集合。
             */
            Set<String> words,

            /**
             * 最大码点词长。
             */
            int maxLength) {
    }

    /**
     * 全组合迭代搜索的单层状态，仅由单次调用拥有。
     *
     * @author bytex0
     * @since 2026-10-06 18:11:36
     */
    private static final class SearchFrame {

        /**
         * 当前起始码点位置。
         */
        private final int start;

        /**
         * 所有候选终点。
         */
        private final List<Integer> ends;

        /**
         * 下一个候选索引。
         */
        private int next;

        /**
         * 创建搜索帧。
         *
         * @param start 起始位置
         * @param ends 候选终点
         */
        private SearchFrame(int start, List<Integer> ends) {
            this.start = start;
            this.ends = ends;
        }
    }
}
