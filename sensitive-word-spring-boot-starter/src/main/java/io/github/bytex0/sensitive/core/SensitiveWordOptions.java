package io.github.bytex0.sensitive.core;

import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 共用配置(SensitiveWordOptions)让原包与根包配置具有相同字段和默认值，不输出词库内容。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class SensitiveWordOptions {

    /**
     * 默认启用，不连接外部服务。
     */
    private boolean enabled = true;

    /**
     * 默认忽略大小写，采用与 Locale 无关的 Unicode 单码点折叠。
     */
    private boolean ignoreCase = true;

    /**
     * 默认跳过空白字符，索引仍指向原文。
     */
    private boolean skipWhitespace = true;

    /**
     * 额外跳过的 BMP 字符，默认空集合，不拆分补充平面码点。
     */
    private Set<Character> skipChars = new HashSet<>();

    /**
     * 原默认最小匹配。
     */
    private MatchType matchType = MatchType.MIN_MATCH;

    /**
     * 原默认替换模式。
     */
    private HandleType handleType = HandleType.REPLACE;

    /**
     * 默认替换字符。
     */
    private char replaceChar = '*';

    /**
     * 可选替换字符串，非空白时优先于 replaceChar 用于默认 process/replaceWithStr。
     */
    private String replaceStr;

    /**
     * 高亮开始标签，标签为可信配置，组件不负责 HTML 转义。
     */
    private String highlightStartTag = "<span class=\"sensitive\">";

    /**
     * 高亮结束标签。
     */
    private String highlightEndTag = "</span>";

    /**
     * classpath 词库路径，支持原无前缀路径以及 classpath:/file: 资源。
     */
    private List<String> dictPaths = new ArrayList<>();

    /**
     * 原外部文件系统词库路径，只接受本地文件。
     */
    private List<String> externalDictPaths = new ArrayList<>();

    /**
     * 初始词库，默认空；无需自行修改源码资源。
     */
    private Set<String> words = new HashSet<>();

    /**
     * 初始白名单，完整匹配范围不参与检测、替换、高亮和拒绝。
     */
    private Set<String> whiteList = new HashSet<>();

    /**
     * 原异常消息模板，占位符对应结构化结果中的词库词条，模块不记录原文。
     */
    private String exceptionMessage = "内容包含敏感词：{}";

    /**
     * 单次文本 UTF-16 长度上限，默认 65536，必须为正数。
     */
    private int maxTextLength = 65536;

    /**
     * 单个词条 UTF-16 长度上限，默认 128，必须为正数。
     */
    private int maxWordLength = 128;

    /**
     * 词库及白名单分别允许的最大词数，默认 10000，必须为正数。
     */
    private int maxWords = 10000;

    /**
     * 单个词库资源的最大字节数，默认 2MiB，必须为正数。
     */
    private int maxDictionaryBytes = 2 * 1024 * 1024;

    /**
     * Web 过滤配置，显式启用后处理 Servlet MVC 的参数、文本和 JSON 字符串值。
     */
    private Web web = new Web();

    /**
     * Web属性(Web)保留原字段契约。
     *
     * @author linshiqiang
     * @since 2026-10-06 08:51:10
     */
    @Data
    public static class Web {

        /**
         * 是否启用 Web 过滤，默认 false。
         */
        private boolean enabled;

        /**
         * 受保护路径，默认空。
         */
        private List<String> urlPatterns = new ArrayList<>();

        /**
         * 排除路径，默认空，优先于受保护路径。
         */
        private List<String> excludePatterns = new ArrayList<>();

        /**
         * 指定检测的请求参数名，默认空表示全部。
         */
        private Set<String> checkParams = new HashSet<>();

        /**
         * 是否检测请求体，默认 true。
         */
        private boolean checkBody = true;

        /**
         * 发现敏感词后的策略，默认拒绝。
         */
        private HandleType handleType = HandleType.EXCEPTION;

        /**
         * 文本/JSON 请求体最大字节数，默认 256KiB，超限返回 413。
         */
        private int maxBodyBytes = 256 * 1024;

        /**
         * JSON 遍历最大深度，默认 64，超限返回 400。
         */
        private int maxJsonDepth = 64;
    }
}
