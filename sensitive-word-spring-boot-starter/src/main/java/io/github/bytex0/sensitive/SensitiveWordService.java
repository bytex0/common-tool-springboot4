package io.github.bytex0.sensitive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.util.Assert;

/**
 * 基于 Aho-Corasick 的原子词库服务，查询和替换共享同一白名单语义。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
public class SensitiveWordService {

    private final boolean ignoreCase;

    private final boolean skipWhitespace;

    private final Trie whitelist;

    private volatile Dictionary dictionary;

    /**
     * 原文 UTF-16 索引，结束位置不包含在结果中。
     *
     * @param word 匹配的原文
     * @param startIndex 开始位置
     * @param endIndex 结束位置
     * @author bytex0
     * @since 2026-10-05 19:21:42
     */
    public record Match(String word, int startIndex, int endIndex) {}

    /**
     * 已编译词库不可变快照。
     *
     * @author bytex0
     * @since 2026-10-05 19:21:42
     */
    private record Dictionary(Set<String> words, Trie trie) {}

    public SensitiveWordService(SensitiveWordProperties properties) {
        ignoreCase = properties.isIgnoreCase();
        skipWhitespace = properties.isSkipWhitespace();
        whitelist = compile(properties.getWhiteList()).trie();
        replaceWords(properties.getWords());
    }

    public synchronized void replaceWords(Set<String> words) {
        dictionary = compile(words);
    }

    public synchronized void addWord(String word) {
        Set<String> next = new HashSet<>(dictionary.words());
        next.add(word);
        replaceWords(next);
    }

    public synchronized void removeWord(String word) {
        Set<String> next = new HashSet<>(dictionary.words());
        next.remove(normalize(Objects.requireNonNull(word)));
        replaceWords(next);
    }

    public int size() {
        return dictionary.words().size();
    }

    public boolean contains(String text) {
        return !findAll(text, true).isEmpty();
    }

    public List<Match> findAll(String text, boolean longest) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Assert.isTrue(text.length() <= 65536, "Text exceeds 65536 UTF-16 units");
        StringBuilder normalized = new StringBuilder();
        int[] starts = new int[text.length()];
        int[] ends = new int[text.length()];
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            int end = offset + Character.charCount(cp);
            if (!(skipWhitespace && Character.isWhitespace(cp))) {
                int before = normalized.length();
                normalized.appendCodePoint(ignoreCase ? Character.toLowerCase(cp) : cp);
                for (int index = before; index < normalized.length(); index++) {
                    starts[index] = offset;
                    ends[index] = end;
                }
            }
            offset = end;
        }
        String input = normalized.toString();
        boolean[] allowed = new boolean[input.length()];
        whitelist.parseText(input, emit -> {
            for (int index = emit.getStart(); index <= emit.getEnd(); index++) {
                allowed[index] = true;
            }
            return true;
        });
        int[] protectedCount = new int[input.length() + 1];
        for (int index = 0; index < input.length(); index++) {
            protectedCount[index + 1] = protectedCount[index] + (allowed[index] ? 1 : 0);
        }
        Emit[] selected = new Emit[input.length()];
        Dictionary snapshot = dictionary;
        snapshot.trie().parseText(input, emit -> {
            int start = emit.getStart();
            if (protectedCount[emit.getEnd() + 1] != protectedCount[start]) {
                return true;
            }
            Emit previous = selected[start];
            if (previous == null || (longest ? emit.getEnd() > previous.getEnd() : emit.getEnd() < previous.getEnd())) {
                selected[start] = emit;
            }
            return true;
        });
        List<Match> matches = new ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            Emit emit = selected[index];
            if (emit != null) {
                int start = starts[index];
                int end = ends[emit.getEnd()];
                matches.add(new Match(text.substring(start, end), start, end));
                index = emit.getEnd();
            }
        }
        return List.copyOf(matches);
    }

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

    public void reject(String text) {
        if (contains(text)) {
            throw new IllegalArgumentException("Text contains prohibited content");
        }
    }

    private Dictionary compile(Set<String> words) {
        Objects.requireNonNull(words, "words");
        Assert.isTrue(words.size() <= 10000, "Dictionary exceeds 10000 words");
        Set<String> normalized = new HashSet<>();
        for (String word : words) {
            Assert.hasText(word, "Words must not be blank");
            Assert.isTrue(word.length() <= 128, "Word exceeds 128 UTF-16 units");
            String value = normalize(word);
            Assert.hasText(value, "Normalized word must not be empty");
            normalized.add(value);
        }
        return new Dictionary(Set.copyOf(normalized), Trie.builder().addKeywords(normalized).build());
    }

    private String normalize(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().filter(cp -> !(skipWhitespace && Character.isWhitespace(cp)))
                .forEach(cp -> result.appendCodePoint(ignoreCase ? Character.toLowerCase(cp) : cp));
        return result.toString();
    }
}
