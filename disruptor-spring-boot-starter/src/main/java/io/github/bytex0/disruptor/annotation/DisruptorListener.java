package io.github.bytex0.disruptor.annotation;

import com.lmax.disruptor.dsl.ProducerType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 队列监听(DisruptorListener)声明一个参数的消费方法，方法返回值不参与消息确认。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DisruptorListener {

    /**
     * 非空且唯一的队列名，同名监听方法会在启动时失败，不覆盖旧队列。
     *
     * @return 队列名
     */
    String value();

    /**
     * 消费工作线程数，默认 1，范围 1 至 128；多线程按序号分配，一条消息仅处理一次。
     *
     * @return 工作线程数
     */
    int threads() default 1;

    /**
     * 是否使用 Java 21 虚拟线程，保留原默认 true，false 使用平台守护线程。
     *
     * @return 是否使用虚拟线程
     */
    boolean virtualThread() default true;

    /**
     * 原生产者模式，默认 MULTI；SINGLE 发布也通过显式锁串行化，允许多个调用线程安全使用。
     *
     * @return 生产者模式
     */
    ProducerType producerType() default ProducerType.MULTI;

    /**
     * 等待策略，默认阻塞；自旋策略可能持续消耗 CPU。
     *
     * @return 等待策略
     */
    WaitStrategyType waitStrategy() default WaitStrategyType.BLOCKING;

    /**
     * 环容量，默认 1024，必须是 2 至 1048576 范围内的二次幂。
     *
     * @return 容量
     */
    int bufferSize() default 1024;

    /**
     * 默认 true 时，保持原默认值的注解属性采用全局配置；设为 false 可固定使用注解全部值。
     *
     * @return 是否继承全局默认
     */
    boolean inheritDefaults() default true;
}
