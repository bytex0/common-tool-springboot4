package io.github.bytex0.sensitive.core;

import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 原过滤器入口(DfaSensitiveWordFilter)使用成熟 Aho-Corasick 引擎，原 API 与分类、索引契约保留。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public class DfaSensitiveWordFilter implements SensitiveWordFilter {

    /**
     * 原始词条、分类、编译结构和选项一并发布。
     */
    private final AtomicReference<Snapshot> state;

    /**
     * 只缓存最近一次白名单编译，不积累历史版本。
     */
    private final AtomicReference<AllowCache> allowed = new AtomicReference<>();

    /**
     * 文本长度上限。
     */
    private final int maxTextLength;

    /**
     * 单词长度上限。
     */
    private final int maxWordLength;

    /**
     * 词数上限。
     */
    private final int maxWords;

    /**
     * 保留原无参构造，不自动添加业务词条。
     */
    public DfaSensitiveWordFilter() {
        this(new SensitiveWordOptions());
    }

    /**
     * 保留原大小写选项构造器。
     *
     * @param ignoreCase 是否忽略大小写
     */
    public DfaSensitiveWordFilter(boolean ignoreCase) {
        this();
        setIgnoreCase(ignoreCase);
    }

    /**
     * 固定容量并创建空词库，资源加载由服务负责。
     *
     * @param options 匹配及资源限制
     */
    public DfaSensitiveWordFilter(SensitiveWordOptions options) {
        Assert.isTrue(options.getMaxTextLength() > 0 && options.getMaxWordLength() > 0 && options.getMaxWords() > 0,
                "敏感词容量配置必须大于零");
        maxTextLength = options.getMaxTextLength();
        maxWordLength = options.getMaxWordLength();
        maxWords = options.getMaxWords();
        state = new AtomicReference<>(compile(Map.of(), options.isIgnoreCase(), options.isSkipWhitespace(),
                Set.copyOf(options.getSkipChars())));
    }

    /**
     * 更新大小写选项并重新编译全部原始词条。
     *
     * @param ignoreCase 新选项
     */
    public void setIgnoreCase(boolean ignoreCase) {
        state.updateAndGet(old -> old.ignoreCase() == ignoreCase ? old
                : compile(old.words(), ignoreCase, old.skipWhitespace(), old.skipChars()));
    }

    /**
     * 更新跳过空白选项，查询和词库使用相同归一化规则。
     *
     * @param skipWhitespace 是否跳过空白
     */
    public void setSkipWhitespace(boolean skipWhitespace) {
        state.updateAndGet(old -> old.skipWhitespace() == skipWhitespace ? old
                : compile(old.words(), old.ignoreCase(), skipWhitespace, old.skipChars()));
    }

    /**
     * 复制额外跳过字符，调用方后续修改集合不会改变已发布状态。
     *
     * @param skipChars 非空字符集合
     */
    public void setSkipChars(Set<Character> skipChars) {
        Set<Character> copy = Set.copyOf(skipChars);
        state.updateAndGet(old -> compile(old.words(), old.ignoreCase(), old.skipWhitespace(), copy));
    }

    /**
     * 完整替换词库，空白或超限数据失败时保留旧词库。
     *
     * @param words 新词条集合
     */
    public void replaceWords(Set<String> words) {
        Objects.requireNonNull(words);
        Map<String, Word> raw = new LinkedHashMap<>();
        for (String word : words.stream().sorted().toList()) {
            Assert.hasText(word, "Words must not be blank");
            raw.put(word, new Word(word, null));
        }
        state.updateAndGet(old -> compile(raw, old.ignoreCase(), old.skipWhitespace(), old.skipChars()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWord(String word) {
        addWord(word, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWord(String word, String category) {
        if (word != null && !word.isBlank()) {
            addWords(Set.of(word), category);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWords(Set<String> words) {
        addWords(words, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWords(Set<String> words, String category) {
        if (words == null || words.isEmpty()) {
            return;
        }
        Assert.isTrue(words.size() <= maxWords, "Dictionary batch exceeds configured word count");
        words.forEach(word -> Assert.isTrue(word == null || word.length() <= maxWordLength,
                "Word exceeds configured UTF-16 limit"));
        List<String> supplied = words.stream().filter(word -> word != null && !word.isBlank()).sorted().toList();
        state.updateAndGet(old -> {
            Map<String, Word> next = new LinkedHashMap<>(old.words());
            Set<String> seen = new HashSet<>(old.indexed().keySet());
            for (String word : supplied) {
                String key = normalize(word, old.ignoreCase(), old.skipWhitespace(), old.skipChars());
                if (seen.add(key)) {
                    next.putIfAbsent(word, new Word(word, category));
                }
            }
            return next.equals(old.words()) ? old : compile(next, old.ignoreCase(), old.skipWhitespace(), old.skipChars());
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeWord(String word) {
        if (word != null && !word.isBlank()) {
            removeWords(Set.of(word));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeWords(Set<String> words) {
        if (words == null || words.isEmpty()) {
            return;
        }
        Set<String> copy = words.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        state.updateAndGet(old -> {
            Set<String> keys = copy.stream().map(word -> normalize(word, old.ignoreCase(), old.skipWhitespace(), old.skipChars()))
                    .collect(Collectors.toSet());
            Map<String, Word> next = new LinkedHashMap<>(old.words());
            next.keySet().removeIf(word -> keys.contains(normalize(word, old.ignoreCase(), old.skipWhitespace(), old.skipChars())));
            return next.equals(old.words()) ? old : compile(next, old.ignoreCase(), old.skipWhitespace(), old.skipChars());
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        state.updateAndGet(old -> compile(Map.of(), old.ignoreCase(), old.skipWhitespace(), old.skipChars()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return state.get().indexed().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(String text) {
        return contains(text, MatchType.MIN_MATCH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(String text, MatchType matchType) {
        return findFirst(text, matchType) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SensitiveWordResult findFirst(String text) {
        return findFirst(text, MatchType.MIN_MATCH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SensitiveWordResult findFirst(String text, MatchType matchType) {
        List<SensitiveWordResult> matches = findAll(text, matchType);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SensitiveWordResult> findAll(String text) {
        return findAll(text, MatchType.MIN_MATCH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SensitiveWordResult> findAll(String text, MatchType matchType) {
        return findAll(text, matchType, Set.of());
    }

    /**
     * 使用同一快照归一化词库、白名单与原文，保留原文区间，不通过删改原文实现白名单。
     *
     * @param text 原文
     * @param matchType 匹配模式
     * @param whiteList 白名单快照
     * @return 原文闭区间结果
     */
    public List<SensitiveWordResult> findAll(String text, MatchType matchType, Set<String> whiteList) {
        Objects.requireNonNull(matchType);
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Assert.isTrue(text.length() <= maxTextLength, "Text exceeds configured UTF-16 limit");
        Snapshot snapshot = state.get();
        Mapping mapped = map(text, snapshot);
        String normalized = mapped.text();
        int[] starts = mapped.starts();
        int[] ends = mapped.ends();
        int[] differences = new int[normalized.length() + 1];
        whiteTrie(snapshot, whiteList).parseText(normalized, emit -> {
            differences[emit.getStart()]++;
            differences[emit.getEnd() + 1]--;
            return true;
        });
        int[] protectedCount = new int[normalized.length() + 1];
        int active = 0;
        for (int index = 0; index < normalized.length(); index++) {
            active += differences[index];
            protectedCount[index + 1] = protectedCount[index] + (active > 0 ? 1 : 0);
        }
        Emit[] selected = new Emit[normalized.length()];
        snapshot.trie().parseText(normalized, emit -> {
            int start = emit.getStart();
            if (protectedCount[emit.getEnd() + 1] != protectedCount[start]) {
                return true;
            }
            Emit previous = selected[start];
            if (previous == null || (matchType == MatchType.MAX_MATCH
                    ? emit.getEnd() > previous.getEnd() : emit.getEnd() < previous.getEnd())) {
                selected[start] = emit;
            }
            return true;
        });
        List<SensitiveWordResult> results = new ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            Emit emit = selected[index];
            if (emit != null) {
                Word word = snapshot.indexed().get(emit.getKeyword());
                results.add(new SensitiveWordResult(word.word(), starts[index], ends[emit.getEnd()] - 1, word.category()));
                index = emit.getEnd();
            }
        }
        return List.copyOf(results);
    }

    /**
     * 返回全部词条覆盖的原文位置，包含相互重叠的词；供自定义过滤器的白名单适配使用。
     *
     * @param text 原文
     * @return 原文位置覆盖标记
     */
    public boolean[] coverage(String text) {
        Assert.isTrue(text.length() <= maxTextLength, "Text exceeds configured UTF-16 limit");
        Snapshot snapshot = state.get();
        Mapping mapped = map(text, snapshot);
        int[] differences = new int[text.length() + 1];
        snapshot.trie().parseText(mapped.text(), emit -> {
            differences[mapped.starts()[emit.getStart()]]++;
            differences[mapped.ends()[emit.getEnd()]]--;
            return true;
        });
        boolean[] covered = new boolean[text.length()];
        int active = 0;
        for (int index = 0; index < covered.length; index++) {
            active += differences[index];
            covered[index] = active > 0;
        }
        return covered;
    }

    /**
     * 生成当前选项对应的原文偏移映射。
     *
     * @param text 原文
     * @param snapshot 当前版本
     * @return 归一化文本和原文边界
     */
    private Mapping map(String text, Snapshot snapshot) {
        StringBuilder input = new StringBuilder();
        int[] starts = new int[Math.multiplyExact(text.length(), 2)];
        int[] ends = new int[starts.length];
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            int end = offset + Character.charCount(cp);
            if (!skip(cp, snapshot.skipWhitespace(), snapshot.skipChars())) {
                int before = input.length();
                input.appendCodePoint(snapshot.ignoreCase() ? Character.toLowerCase(Character.toUpperCase(cp)) : cp);
                for (int index = before; index < input.length(); index++) {
                    starts[index] = offset;
                    ends[index] = end;
                }
            }
            offset = end;
        }
        return new Mapping(input.toString(), starts, ends);
    }

    /**
     * 白名单编译只缓存最近状态，词库/配置更新不会复用旧归一化规则。
     *
     * @param snapshot 当前版本
     * @param whiteList 白名单
     * @return 编译结构
     */
    private Trie whiteTrie(Snapshot snapshot, Set<String> whiteList) {
        AllowCache cached = allowed.get();
        if (cached != null && cached.snapshot() == snapshot && cached.words().equals(whiteList)) {
            return cached.trie();
        }
        Map<String, Word> entries = new LinkedHashMap<>();
        for (String word : whiteList) {
            entries.put(word, new Word(word, null));
        }
        Trie trie = compile(entries, snapshot.ignoreCase(), snapshot.skipWhitespace(), snapshot.skipChars()).trie();
        allowed.set(new AllowCache(snapshot, Set.copyOf(whiteList), trie));
        return trie;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String replace(String text, char replacement) {
        return replace(text, replacement, MatchType.MIN_MATCH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String replace(String text, char replacement, MatchType matchType) {
        return SensitiveText.rewrite(text, findAll(text, matchType),
                match -> String.valueOf(replacement).repeat(match.getEndIndex() - match.getStartIndex() + 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String replace(String text, String replacement) {
        Objects.requireNonNull(replacement);
        return SensitiveText.rewrite(text, findAll(text), match -> replacement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String highlight(String text, String startTag, String endTag) {
        return highlight(text, startTag, endTag, MatchType.MIN_MATCH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String highlight(String text, String startTag, String endTag, MatchType matchType) {
        Objects.requireNonNull(startTag);
        Objects.requireNonNull(endTag);
        return SensitiveText.rewrite(text, findAll(text, matchType),
                match -> startTag + text.substring(match.getStartIndex(), match.getEndIndex() + 1) + endTag);
    }

    /**
     * 校验全部词条后构建快照，失败不影响已经发布的版本。
     *
     * @param words 原始词条
     * @param ignoreCase 大小写策略
     * @param whitespace 空白策略
     * @param skipChars 额外跳过字符
     * @return 编译快照
     */
    private Snapshot compile(Map<String, Word> words, boolean ignoreCase, boolean whitespace, Set<Character> skipChars) {
        Assert.isTrue(words.size() <= maxWords, "Dictionary exceeds configured word count");
        Map<String, Word> indexed = new LinkedHashMap<>();
        for (Word entry : words.values()) {
            Assert.hasText(entry.word(), "Words must not be blank");
            Assert.isTrue(entry.word().length() <= maxWordLength, "Word exceeds configured UTF-16 limit");
            String key = normalize(entry.word(), ignoreCase, whitespace, skipChars);
            Assert.hasText(key, "Normalized word must not be empty");
            indexed.putIfAbsent(key, entry);
        }
        return new Snapshot(Collections.unmodifiableMap(new LinkedHashMap<>(words)), Map.copyOf(indexed),
                Trie.builder().addKeywords(indexed.keySet()).build(), ignoreCase, whitespace, Set.copyOf(skipChars));
    }

    /**
     * 单码点归一化不依赖系统语言，也不扩展字符后误用原文下标。
     *
     * @param value 原字符串
     * @param ignoreCase 大小写策略
     * @param whitespace 空白策略
     * @param skipChars 额外字符
     * @return 归一化值
     */
    private String normalize(String value, boolean ignoreCase, boolean whitespace, Set<Character> skipChars) {
        StringBuilder result = new StringBuilder();
        value.codePoints().filter(cp -> !skip(cp, whitespace, skipChars))
                .forEach(cp -> result.appendCodePoint(ignoreCase ? Character.toLowerCase(Character.toUpperCase(cp)) : cp));
        return result.toString();
    }

    /**
     * 判断完整码点是否应跳过，不单独删除代理项。
     *
     * @param codePoint 码点
     * @param whitespace 空白策略
     * @param skipChars 额外 BMP 字符
     * @return 是否跳过
     */
    private boolean skip(int codePoint, boolean whitespace, Set<Character> skipChars) {
        return whitespace && Character.isWhitespace(codePoint)
                || Character.isBmpCodePoint(codePoint) && skipChars.contains((char) codePoint);
    }

    /**
     * 索引映射(Mapping)仅由一次调用使用，不发布可修改数组到外部。
     *
     * @author linshiqiang
     * @since 2026-10-06 08:51:10
     * @param text 归一化文本
     * @param starts 原文起始偏移
     * @param ends 原文右开结束偏移
     */
    private record Mapping(
            /**
             * 归一化文本。
             */
            String text,

            /**
             * 原文起始偏移。
             */
            int[] starts,

            /**
             * 原文右开结束偏移。
             */
            int[] ends) {
    }

    /**
     * 原词条(Word)保存注册时的拼写和分类。
     *
     * @author linshiqiang
     * @since 2026-10-06 08:51:10
     * @param word 原词条
     * @param category 分类
     */
    private record Word(
            /**
             * 注册拼写。
             */
            String word,

            /**
             * 可选分类。
             */
            String category) {
    }

    /**
     * 原子版本(Snapshot)将配置和编译结构一起发布。
     *
     * @author linshiqiang
     * @since 2026-10-06 08:51:10
     * @param words 原词条
     * @param indexed 归一化索引
     * @param trie 编译结构
     * @param ignoreCase 大小写策略
     * @param skipWhitespace 空白策略
     * @param skipChars 额外字符
     */
    private record Snapshot(
            /**
             * 保留插入顺序的原词条。
             */
            Map<String, Word> words,

            /**
             * 归一化词到分类信息。
             */
            Map<String, Word> indexed,

            /**
             * 编译后只读匹配器。
             */
            Trie trie,

            /**
             * 大小写策略。
             */
            boolean ignoreCase,

            /**
             * 空白策略。
             */
            boolean skipWhitespace,

            /**
             * 额外跳过字符。
             */
            Set<Character> skipChars) {
    }

    /**
     * 单版本白名单缓存(AllowCache)不积累历史规则。
     *
     * @author linshiqiang
     * @since 2026-10-06 08:51:10
     * @param snapshot 对应词库版本
     * @param words 白名单
     * @param trie 编译结果
     */
    private record AllowCache(
            /**
             * 对应版本。
             */
            Snapshot snapshot,

            /**
             * 白名单快照。
             */
            Set<String> words,

            /**
             * 只读白名单匹配器。
             */
            Trie trie) {
    }
}
