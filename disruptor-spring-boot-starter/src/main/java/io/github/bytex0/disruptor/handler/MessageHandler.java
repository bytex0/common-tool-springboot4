package io.github.bytex0.disruptor.handler;

import io.github.bytex0.disruptor.event.DisruptorEvent;

/**
 * 原事件消费者(MessageHandler)保留基于 DisruptorEvent 的函数式接口。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 * @param <T> 消息类型
 */
@FunctionalInterface
public interface MessageHandler<T> {

    /**
     * 同步处理当前事件，不保留复用事件对象。
     *
     * @param event 当前事件
     */
    void handle(DisruptorEvent<T> event);
}
