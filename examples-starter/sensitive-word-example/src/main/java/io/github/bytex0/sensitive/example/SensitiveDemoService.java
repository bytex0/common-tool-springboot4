package io.github.bytex0.sensitive.example;

import io.github.bytex0.sensitive.annotation.SensitiveWordCheck;
import io.github.bytex0.sensitive.annotation.SensitiveWordField;
import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.MatchType;
import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * 注解示例(SensitiveDemoService)由 Spring 代理触发原方法、参数及字段策略。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:21:47
 */
@Service
public class SensitiveDemoService {

    /**
     * 参数注解可独立触发，不影响第二个未标记参数。
     *
     * @param checked 受保护文本
     * @param other 不受保护文本
     * @return 处理结果
     */
    public String parameter(@SensitiveWordCheck(handleType = HandleType.REPLACE) String checked, String other) {
        return checked + ":" + other;
    }

    /**
     * 字段覆盖方法策略，仅选择 content，不隐式修改未选字段。
     *
     * @param document 请求对象
     * @return 处理后的对象
     */
    @SensitiveWordCheck(fields = "content")
    public Document document(Document document) {
        return document;
    }

    /**
     * 默认拒绝策略。
     *
     * @param text 输入
     * @return 未命中的文本
     */
    @SensitiveWordCheck
    public String reject(String text) {
        return text;
    }

    /**
     * 高亮策略使用配置标签，不自动转义原文。
     *
     * @param text 原文
     * @return 高亮结果
     */
    @SensitiveWordCheck(handleType = HandleType.HIGHLIGHT)
    public String highlight(String text) {
        return text;
    }

    /**
     * 仅检测策略不修改文本。
     *
     * @param text 原文
     * @return 原文
     */
    @SensitiveWordCheck(handleType = HandleType.DETECT_ONLY)
    public String detect(String text) {
        return text;
    }

    /**
     * 文本请求(Document)用于字段优先级和未选字段验证。
     *
     * @author linshiqiang
     * @since 2026-10-06 09:21:47
     */
    @Data
    public static class Document {

        /**
         * 采用字段最长匹配和替换，覆盖方法默认拒绝策略。
         */
        @SensitiveWordField(matchType = MatchType.MAX_MATCH)
        private String content;

        /**
         * 未被 fields 选择，不参与本接口过滤。
         */
        private String other;
    }
}
