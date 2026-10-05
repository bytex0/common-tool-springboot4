package io.github.bytex0.sensitive;

import java.util.List;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实例隔离的词库和文本匹配配置。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
@Data
@ConfigurationProperties("sensitive-word")
public class SensitiveWordProperties {

    /**
     * 本地处理默认启用，不访问外部服务。
     */
    private boolean enabled = true;

    /**
     * Unicode 单码点大小写折叠，不依赖系统 Locale。
     */
    private boolean ignoreCase = true;

    /**
     * 匹配时忽略空白，返回索引仍指向原文。
     */
    private boolean skipWhitespace = true;

    /**
     * 初始敏感词，最多一万个。
     */
    private Set<String> words = Set.of();

    /**
     * 白名单词，完整命中的范围不参加处理。
     */
    private Set<String> whiteList = Set.of();

    /**
     * UTF-8 词库资源，每行一词，# 开头为注释。
     */
    private List<String> dictPaths = List.of();
}
