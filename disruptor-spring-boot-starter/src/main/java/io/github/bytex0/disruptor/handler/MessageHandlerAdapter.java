package io.github.bytex0.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import io.github.bytex0.disruptor.event.DisruptorEvent;

import java.util.Objects;

/**
 * 原消费者适配(MessageHandlerAdapter)保持事件对象转发，不私自改变外部消费链的生命周期。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 * @param <T> 消息类型
 */
public class MessageHandlerAdapter<T> implements EventHandler<DisruptorEvent<T>> {

    /**
     * 调用方提供的消费者。
     */
    private final MessageHandler<T> handler;

    /**
     * 保留原构造器。
     *
     * @param handler 消费者
     */
    public MessageHandlerAdapter(MessageHandler<T> handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEvent(DisruptorEvent<T> event, long sequence, boolean endOfBatch) {
        handler.handle(event);
    }
}
