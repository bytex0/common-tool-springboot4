package io.github.bytex0.disruptor;

/**
 * 由 Spring 管理的单队列顺序消费者定义。
 *
 * @param <T> 消息类型
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
public interface MessageHandler<T> {

    String name();

    Class<T> type();

    void handle(T message) throws Exception;
}
