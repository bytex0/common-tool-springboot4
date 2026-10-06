package io.github.bytex0.sensitive.core;

import java.util.List;

/**
 * 原业务异常(SensitiveWordException)保留构造器及词条查询，防止调用方篡改异常证据。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public class SensitiveWordException extends RuntimeException {

    /**
     * 序列化版本。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 可选检测结果，构造时深复制。
     */
    private final List<SensitiveWordResult> sensitiveWords;

    /**
     * 保留原消息构造器。
     *
     * @param message 消息
     * @param sensitiveWords 检测结果，可为空
     */
    public SensitiveWordException(String message, List<SensitiveWordResult> sensitiveWords) {
        this(message, null, sensitiveWords);
    }

    /**
     * 保留原原因链构造器。
     *
     * @param message 消息
     * @param cause 原因
     * @param sensitiveWords 可选检测结果
     */
    public SensitiveWordException(String message, Throwable cause, List<SensitiveWordResult> sensitiveWords) {
        super(message, cause);
        this.sensitiveWords = copy(sensitiveWords);
    }

    /**
     * 返回结果副本，保持原 null 约定。
     *
     * @return 检测结果副本
     */
    public List<SensitiveWordResult> getSensitiveWords() {
        return copy(sensitiveWords);
    }

    /**
     * 返回首个词库词条。
     *
     * @return 词条或 null
     */
    public String getFirstSensitiveWord() {
        return sensitiveWords == null || sensitiveWords.isEmpty() ? null : sensitiveWords.getFirst().getWord();
    }

    /**
     * 返回不可变词条列表。
     *
     * @return 词条列表
     */
    public List<String> getAllSensitiveWords() {
        return sensitiveWords == null ? List.of() : sensitiveWords.stream().map(SensitiveWordResult::getWord).toList();
    }

    /**
     * 复制可变结果模型。
     *
     * @param values 原结果
     * @return 副本或 null
     */
    private static List<SensitiveWordResult> copy(List<SensitiveWordResult> values) {
        return values == null ? null : values.stream().map(value -> new SensitiveWordResult(value.getWord(),
                value.getStartIndex(), value.getEndIndex(), value.getCategory())).toList();
    }
}
