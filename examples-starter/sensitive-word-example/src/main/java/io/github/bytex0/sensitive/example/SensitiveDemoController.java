package io.github.bytex0.sensitive.example;

import io.github.bytex0.sensitive.core.MatchType;
import io.github.bytex0.sensitive.core.SensitiveWordException;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.example.SensitiveDemoService.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/**
 * 原能力联调(SensitiveDemoController)集成真实服务、注解和 Web 过滤，不暴露任意文件读取。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:21:47
 */
@RestController
@RequestMapping("/api/sensitive")
public class SensitiveDemoController {

    /**
     * 原包和现代门面共享的业务组件。
     */
    private final SensitiveWordOperations operations;

    /**
     * 实际 Spring 代理服务。
     */
    private final SensitiveDemoService annotations;

    /**
     * 注入 Starter 组件，不复制匹配实现。
     *
     * @param operations 业务组件
     * @param annotations 注解服务
     */
    public SensitiveDemoController(SensitiveWordOperations operations, SensitiveDemoService annotations) {
        this.operations = operations;
        this.annotations = annotations;
    }

    /**
     * 验证旧结果分类/闭区间及替换、高亮重载。
     *
     * @param text 原文
     * @param mode 匹配模式
     * @return 结构化结果
     */
    @GetMapping("/legacy")
    public Map<String, Object> legacy(@RequestParam String text, @RequestParam(defaultValue = "MIN_MATCH") MatchType mode) {
        return Map.of("code", 0, "data", Map.of("matches", operations.findAll(text, mode),
                "replaced", operations.replace(text, '#', mode), "highlight", operations.highlight(text, "<b>", "</b>", mode)));
    }

    /**
     * 原批量添加分类词入口。
     *
     * @param words 词条
     * @param category 可选分类
     * @return 当前词数
     */
    @PutMapping("/words")
    public Map<String, Object> words(@RequestBody Set<String> words, @RequestParam(required = false) String category) {
        operations.addWords(words, category);
        return Map.of("code", 0, "data", Map.of("size", operations.size()));
    }

    /**
     * 删除一个词条。
     *
     * @param word 原词条或等价归一化词
     * @return 操作结果
     */
    @DeleteMapping("/words")
    public Map<String, Object> remove(@RequestParam String word) {
        operations.removeWord(word);
        return Map.of("code", 0);
    }

    /**
     * 清空当前实例词库。
     *
     * @return 清空后的词数
     */
    @DeleteMapping("/words/all")
    public Map<String, Object> clear() {
        operations.clear();
        return Map.of("code", 0, "data", Map.of("size", operations.size()));
    }

    /**
     * 动态添加白名单。
     *
     * @param words 白名单
     * @return 操作结果
     */
    @PutMapping("/whitelist")
    public Map<String, Object> whitelist(@RequestBody Set<String> words) {
        operations.addWhiteList(words);
        return Map.of("code", 0);
    }

    /**
     * 删除指定白名单词。
     *
     * @param word 注册的词条
     * @return 操作结果
     */
    @DeleteMapping("/whitelist")
    public Map<String, Object> removeWhitelist(@RequestParam String word) {
        operations.removeWhiteList(word);
        return Map.of("code", 0);
    }

    /**
     * 仅加载固定的测试资源，不接受外部文件路径。
     *
     * @return 词数
     */
    @PostMapping("/load-example")
    public Map<String, Object> load() {
        operations.loadFromClasspath("sensitive/default.txt");
        return Map.of("code", 0, "data", Map.of("size", operations.size()));
    }

    /**
     * 通过独立参数注解替换。
     *
     * @param text 受保护文本
     * @param other 不受保护文本
     * @return 结果
     */
    @PostMapping("/annotations/parameter")
    public Map<String, Object> parameter(@RequestParam String text, @RequestParam(defaultValue = "") String other) {
        return Map.of("code", 0, "data", Map.of("text", annotations.parameter(text, other)));
    }

    /**
     * 通过对象字段注解处理。
     *
     * @param document 请求
     * @return 请求字段结果
     */
    @PostMapping("/annotations/document")
    public Map<String, Object> document(@RequestBody Document document) {
        return Map.of("code", 0, "data", annotations.document(document));
    }

    /**
     * 通过方法注解拒绝敏感正文。
     *
     * @param text 正文
     * @return 未命中的原文
     */
    @PostMapping("/annotations/reject")
    public Map<String, Object> reject(@RequestBody String text) {
        return Map.of("code", 0, "data", Map.of("text", annotations.reject(text)));
    }

    /**
     * 验证高亮和仅检测两种注解策略。
     *
     * @param text 输入
     * @return 两种结果
     */
    @PostMapping("/annotations/modes")
    public Map<String, Object> modes(@RequestBody String text) {
        return Map.of("code", 0, "data", Map.of("highlight", annotations.highlight(text), "detected", annotations.detect(text)));
    }

    /**
     * Web 过滤后的 JSON 按原结构返回。
     *
     * @param body 处理后的树
     * @return JSON 树
     */
    @PostMapping(value = "/web/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> json(@RequestBody JsonNode body) {
        return Map.of("code", 0, "data", body);
    }

    /**
     * Web 过滤后的文本回显。
     *
     * @param text 文本
     * @return 结果
     */
    @PostMapping(value = "/web/text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> text(@RequestBody String text) {
        return Map.of("code", 0, "data", Map.of("text", text));
    }

    /**
     * 排除路径不应被 Web 过滤器改写。
     *
     * @param text 原文
     * @return 原文
     */
    @PostMapping("/web/excluded")
    public Map<String, Object> excluded(@RequestBody String text) {
        return Map.of("code", 0, "data", Map.of("text", text));
    }

    /**
     * 验证 Web 参数名称选择。
     *
     * @param selected 受保护参数
     * @param other 其他参数
     * @return 实际收到的参数
     */
    @GetMapping("/web/query")
    public Map<String, Object> query(@RequestParam(defaultValue = "") String selected, @RequestParam(defaultValue = "") String other) {
        return Map.of("code", 0, "data", Map.of("selected", selected, "other", other));
    }

    /**
     * 对外错误只包含通用状态，不回传敏感正文或内部匹配证据。
     *
     * @return 错误状态
     */
    @ExceptionHandler({SensitiveWordException.class, IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Integer> invalid() {
        return Map.of("code", 400);
    }
}
