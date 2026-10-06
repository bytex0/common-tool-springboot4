package io.github.bytex0.sensitive.core;

import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 敏感词业务(SensitiveWordOperations)统一原服务与当前门面，白名单对所有处理入口一致生效。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public class SensitiveWordOperations implements SensitiveWordActions, InitializingBean {

    /**
     * 可由使用方替换的过滤器。
     */
    private final SensitiveWordFilter filter;

    /**
     * 原处理配置，运行时改词库使用显式管理接口。
     */
    private final SensitiveWordOptions options;

    /**
     * 当前容器资源加载器。
     */
    private final ResourceLoader resources;

    /**
     * 白名单及其编译结构一并发布。
     */
    private final AtomicReference<WhiteList> whiteList;

    /**
     * 原初始化入口只执行一次，失败时允许重试。
     */
    private final AtomicBoolean initialized = new AtomicBoolean();

    /**
     * 创建独立业务服务，初始化由调用方或 Spring 生命周期触发。
     *
     * @param filter 过滤器
     * @param options 选项
     */
    public SensitiveWordOperations(SensitiveWordFilter filter, SensitiveWordOptions options) {
        this(filter, options, new DefaultResourceLoader());
    }

    /**
     * 注入容器资源解析方式，不在构造时读取外部文件。
     *
     * @param filter 过滤器
     * @param options 配置
     * @param resources 资源加载器
     */
    public SensitiveWordOperations(SensitiveWordFilter filter, SensitiveWordOptions options, ResourceLoader resources) {
        this.filter = Objects.requireNonNull(filter);
        this.options = Objects.requireNonNull(options);
        this.resources = Objects.requireNonNull(resources);
        Assert.isTrue(options.getMaxDictionaryBytes() > 0 && options.getMaxDictionaryBytes() < Integer.MAX_VALUE,
                "词库资源大小上限必须为正数且小于Integer.MAX_VALUE");
        whiteList = new AtomicReference<>(compileWhite(Set.of()));
    }

    /**
     * 先读取并验证全部资源，再添加词条，加载失败不静默继续启动。
     */
    @Override
    public void afterPropertiesSet() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        try {
            WhiteList allowed = compileWhite(Set.copyOf(options.getWhiteList()));
            Set<String> words = new HashSet<>(options.getWords());
            for (String path : options.getDictPaths()) {
                words.addAll(read(path.startsWith("file:") ? path : classpath(path)));
            }
            for (String path : options.getExternalDictPaths()) {
                words.addAll(read(file(path)));
            }
            DfaSensitiveWordFilter validation = new DfaSensitiveWordFilter(options);
            validation.replaceWords(words);
            filter.addWords(words);
            whiteList.set(allowed);
        } catch (RuntimeException failure) {
            initialized.set(false);
            throw failure;
        }
    }

    /**
     * 原过滤器访问器，不转移业务管理权。
     *
     * @return 当前过滤器
     */
    public SensitiveWordFilter getFilter() {
        return filter;
    }

    /**
     * 原 classpath 词库加载入口，读取失败保留原异常。
     *
     * @param path classpath 路径，可带 classpath: 前缀
     */
    public void loadFromClasspath(String path) {
        filter.addWords(read(classpath(path)));
    }

    /**
     * 原文件加载入口，不允许网络 URI。
     *
     * @param path 本地路径或 file: URI
     */
    public void loadFromFile(String path) {
        filter.addWords(read(file(path)));
    }

    /**
     * 规范化原无前缀 classpath 路径。
     *
     * @param path 路径
     * @return classpath 资源标识
     */
    private String classpath(String path) {
        Assert.hasText(path, "词库路径不能为空");
        Assert.isTrue(!path.contains(":") || path.startsWith("classpath:"), "classpath词库不允许网络地址");
        return path.startsWith("classpath:") ? path : "classpath:" + path;
    }

    /**
     * 规范化本地文件资源，不将任意 URI 当作文件。
     *
     * @param path 本地路径
     * @return file 资源标识
     */
    private String file(String path) {
        Assert.hasText(path, "词库路径不能为空");
        Assert.isTrue(!path.contains("://") || path.startsWith("file:"), "词库只允许本地文件");
        return path.startsWith("file:") ? path : Path.of(path).toUri().toString();
    }

    /**
     * 有界读取 UTF-8 资源，移除文件首 BOM，跳过空行和注释并自动关闭流。
     *
     * @param path 可信资源标识
     * @return 词条集合
     */
    private Set<String> read(String path) {
        Assert.isTrue(path.startsWith("classpath:") || path.startsWith("file:"), "词库必须为本地资源");
        Resource resource = resources.getResource(path);
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readNBytes(options.getMaxDictionaryBytes() + 1);
            Assert.isTrue(bytes.length <= options.getMaxDictionaryBytes(), "词库资源超过配置上限");
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            Set<String> words = new HashSet<>();
            text.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(words::add);
            return Set.copyOf(words);
        } catch (IOException failure) {
            throw new UncheckedIOException("读取敏感词库失败", failure);
        }
    }

    /**
     * 按配置的默认模式检测，包含白名单语义。
     *
     * @param text 原文
     * @return 是否命中
     */
    @Override
    public boolean contains(String text) {
        return contains(text, options.getMatchType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(String text, MatchType matchType) {
        return findFirst(text, matchType) != null;
    }

    /**
     * 按配置查询首个结果。
     *
     * @param text 原文
     * @return 结果或 null
     */
    @Override
    public SensitiveWordResult findFirst(String text) {
        return findFirst(text, options.getMatchType());
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
     * 按配置查询全部结果。
     *
     * @param text 原文
     * @return 非重叠结果
     */
    @Override
    public List<SensitiveWordResult> findAll(String text) {
        return findAll(text, options.getMatchType());
    }

    /**
     * 查询和白名单使用同一次快照；自定义过滤器仍按原闭区间协议返回结果。
     *
     * @param text 原文
     * @param matchType 策略
     * @return 考虑白名单后的结果
     */
    @Override
    public List<SensitiveWordResult> findAll(String text, MatchType matchType) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Assert.isTrue(text.length() <= options.getMaxTextLength(), "Text exceeds configured UTF-16 limit");
        WhiteList allowed = whiteList.get();
        if (filter instanceof DfaSensitiveWordFilter compiled) {
            return compiled.findAll(text, matchType, allowed.words());
        }
        boolean[] covered = allowed.filter().coverage(text);
        char[] masked = text.toCharArray();
        int[] protectedCount = new int[text.length() + 1];
        for (int index = 0; index < masked.length; index++) {
            if (covered[index]) {
                masked[index] = '\0';
            }
            protectedCount[index + 1] = protectedCount[index] + (covered[index] ? 1 : 0);
        }
        return filter.findAll(new String(masked), matchType).stream().filter(match -> {
            Assert.isTrue(match.getStartIndex() >= 0 && match.getEndIndex() >= match.getStartIndex()
                    && match.getEndIndex() < text.length(), "自定义过滤器返回了无效区间");
            return protectedCount[match.getEndIndex() + 1] == protectedCount[match.getStartIndex()];
        }).toList();
    }

    /**
     * 使用配置字符与匹配模式替换，保持原字符入口。
     *
     * @param text 原文
     * @return 结果
     */
    public String replace(String text) {
        return replace(text, options.getReplaceChar());
    }

    /**
     * 使用配置匹配模式及显式字符。
     *
     * @param text 原文
     * @param replacement 字符
     * @return 结果
     */
    @Override
    public String replace(String text, char replacement) {
        return replace(text, replacement, options.getMatchType());
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
     * 固定字符串替换使用配置的匹配模式，白名单仍生效。
     *
     * @param text 原文
     * @param replacement 替换串
     * @return 结果
     */
    @Override
    public String replace(String text, String replacement) {
        Objects.requireNonNull(replacement);
        return SensitiveText.rewrite(text, findAll(text), match -> replacement);
    }

    /**
     * 使用配置的非空白替换串，否则回落配置字符。
     *
     * @param text 原文
     * @return 结果
     */
    public String replaceWithStr(String text) {
        return options.getReplaceStr() != null && !options.getReplaceStr().isBlank()
                ? replace(text, options.getReplaceStr()) : replace(text);
    }

    /**
     * 使用配置标签高亮。
     *
     * @param text 原文
     * @return 高亮结果
     */
    public String highlight(String text) {
        return highlight(text, options.getHighlightStartTag(), options.getHighlightEndTag());
    }

    /**
     * 使用配置的匹配策略和显式标签。
     *
     * @param text 原文
     * @param startTag 起始标签
     * @param endTag 结束标签
     * @return 结果
     */
    @Override
    public String highlight(String text, String startTag, String endTag) {
        return highlight(text, startTag, endTag, options.getMatchType());
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
     * 按原配置处理，替换字符串配置优先于字符，异常不记录敏感正文。
     *
     * @param text 原文
     * @return 处理结果
     */
    public String process(String text) {
        if (options.getHandleType() == HandleType.REPLACE) {
            return replaceWithStr(text);
        }
        return process(text, options.getHandleType(), options.getMatchType(), options.getReplaceChar(), options.getExceptionMessage());
    }

    /**
     * 供注解及 Web 层使用统一处理语义，仅查询一次。
     *
     * @param text 原文
     * @param handling 处理策略
     * @param matching 匹配策略
     * @param replacement 显式替换字符
     * @param message 拒绝消息模板
     * @return 结果
     */
    public String process(String text, HandleType handling, MatchType matching, char replacement, String message) {
        List<SensitiveWordResult> matches = findAll(text, matching);
        if (matches.isEmpty()) {
            return text;
        }
        return switch (handling) {
            case DETECT_ONLY -> text;
            case EXCEPTION -> throw new SensitiveWordException(MessageFormatter.arrayFormat(message,
                    new Object[]{matches.stream().map(SensitiveWordResult::getWord).toList()}).getMessage(), matches);
            case REPLACE -> SensitiveText.rewrite(text, matches,
                    match -> String.valueOf(replacement).repeat(match.getEndIndex() - match.getStartIndex() + 1));
            case HIGHLIGHT -> SensitiveText.rewrite(text, matches, match -> options.getHighlightStartTag()
                    + text.substring(match.getStartIndex(), match.getEndIndex() + 1) + options.getHighlightEndTag());
        };
    }

    /**
     * 原子替换内置过滤器词库，自定义实现须显式提供可原子替换的过滤器。
     *
     * @param words 全量词条
     */
    public void replaceWords(Set<String> words) {
        Assert.state(filter instanceof DfaSensitiveWordFilter, "整体替换需要支持快照的DfaSensitiveWordFilter");
        ((DfaSensitiveWordFilter) filter).replaceWords(words);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWord(String word) {
        filter.addWord(word);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWord(String word, String category) {
        filter.addWord(word, category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWords(Set<String> words) {
        filter.addWords(words);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWords(Set<String> words, String category) {
        filter.addWords(words, category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeWord(String word) {
        filter.removeWord(word);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeWords(Set<String> words) {
        filter.removeWords(words);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        filter.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return filter.size();
    }

    /**
     * 添加原动态白名单词。
     *
     * @param word 非空词条
     */
    public void addWhiteList(String word) {
        addWhiteList(Set.of(word));
    }

    /**
     * 原子增加白名单，失败不破坏旧版本。
     *
     * @param words 词集合
     */
    public void addWhiteList(Set<String> words) {
        Set<String> copy = Set.copyOf(words);
        whiteList.updateAndGet(old -> {
            Set<String> next = new HashSet<>(old.words());
            next.addAll(copy);
            return compileWhite(next);
        });
    }

    /**
     * 删除指定白名单词。
     *
     * @param word 原始注册词
     */
    public void removeWhiteList(String word) {
        whiteList.updateAndGet(old -> {
            Set<String> next = new HashSet<>(old.words());
            next.remove(word);
            return compileWhite(next);
        });
    }

    /**
     * 完整替换白名单，支持显式清空。
     *
     * @param words 词集合
     */
    public void replaceWhiteList(Set<String> words) {
        whiteList.set(compileWhite(Set.copyOf(words)));
    }

    /**
     * 编译并验证白名单快照。
     *
     * @param words 词集合
     * @return 不可变使用的状态
     */
    private WhiteList compileWhite(Set<String> words) {
        DfaSensitiveWordFilter matcher = new DfaSensitiveWordFilter(options);
        matcher.replaceWords(words);
        return new WhiteList(Set.copyOf(words), matcher);
    }

    /**
     * 白名单版本(WhiteList)对自定义过滤器提供相同原文屏蔽语义。
     *
     * @author linshiqiang
     * @since 2026-10-06 08:51:10
     * @param words 原始白名单
     * @param filter 仅查询的编译结构
     */
    private record WhiteList(
            /**
             * 不可变词集合。
             */
            Set<String> words,

            /**
             * 仅用于匹配，发布后不再修改。
             */
            DfaSensitiveWordFilter filter) {
    }
}
