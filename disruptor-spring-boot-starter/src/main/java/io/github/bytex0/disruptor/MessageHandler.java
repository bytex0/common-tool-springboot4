package io.github.bytex0.disruptor;

/**
 * 由 Spring 管理的单队列顺序消费者定义。
 *
 * @param <T> 消息类型
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
public interface MessageHandler<T> {

    /**
     * 返回当前模板内唯一的非空队列名。
     *
     * @return 队列名
     */
    String name();

    /**
     * 声明消息类型，发布前验证不兼容输入。
     *
     * @return 消息类型
     */
    Class<T> type();

    /**
     * 同步消费，异常传递给发布者的消费确认。
     *
     * @param message 当前消息
     * @throws Exception 业务失败
     */
    void handle(T message) throws Exception;
}
