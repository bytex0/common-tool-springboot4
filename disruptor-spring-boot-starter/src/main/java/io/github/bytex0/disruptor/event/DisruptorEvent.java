package io.github.bytex0.disruptor.event;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.concurrent.CompletableFuture;

/**
 * 可复用事件(DisruptorEvent)保留原 data 模型，调用方不得在消费结束后保留事件引用。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 * @param <T> 消息类型
 */
@Data
public class DisruptorEvent<T> {

    /**
     * 当前消息，消费完成后由托管队列清空。
     */
    private T data;

    /**
     * 内部消费确认，不参与原模型的相等判断或字符串输出。
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private CompletableFuture<Void> completion;

    /**
     * 绑定本次发布的确认；外部原生发布可不设置。
     *
     * @param completion 可为空的确认
     */
    public void bindCompletion(CompletableFuture<Void> completion) {
        this.completion = completion;
    }

    /**
     * 获取当前内部确认，不允许消费者自行提前完成。
     *
     * @return 确认或 null
     */
    public CompletableFuture<Void> completion() {
        return completion;
    }

    /**
     * 消费完成后释放业务与确认引用。
     */
    public void clear() {
        data = null;
        completion = null;
    }
}
