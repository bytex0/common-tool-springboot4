package io.github.bytex0.disruptor.template;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.github.bytex0.disruptor.config.DisruptorProperties;
import io.github.bytex0.disruptor.core.DisruptorEngine;
import io.github.bytex0.disruptor.event.DisruptorEvent;
import io.github.bytex0.disruptor.handler.MessageHandler;
import io.github.bytex0.disruptor.handler.MessageHandlerAdapter;
import io.github.bytex0.disruptor.monitor.DisruptorMetrics;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

/**
 * 原模板入口(DisruptorTemplate)保留 void send、动态建队列和注册能力，共享当前安全队列引擎。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:17:49
 */
public class DisruptorTemplate implements AutoCloseable {

    /**
     * 实际引擎。
     */
    private final DisruptorEngine engine;

    /**
     * 原直接构造方式自行关闭引擎，共享注入不重复关闭。
     */
    private final boolean owned;

    /**
     * 保留原可选指标构造方式，不立即启动任何队列。
     *
     * @param metrics 可选指标组件
     */
    public DisruptorTemplate(DisruptorMetrics metrics) {
        this.engine = new DisruptorEngine(List.of(), new DisruptorProperties(), metrics);
        this.owned = true;
    }

    /**
     * 注入与现代入口共享的引擎。
     *
     * @param engine 当前引擎
     * @param externallyManaged true 表示由外部管理关闭，避免与原 null 指标构造器产生歧义
     */
    public DisruptorTemplate(DisruptorEngine engine, boolean externallyManaged) {
        this.engine = engine;
        this.owned = !externallyManaged;
    }

    /**
     * 保留原发布入口，返回只表示已进入队列，不表示业务成功。
     *
     * @param queueName 队列名称
     * @param data 原消息，可为空
     * @param <T> 消息类型
     */
    public <T> void send(String queueName, T data) {
        engine.sendLegacy(queueName, data);
    }

    /**
     * 对托管队列提供可观察的消费确认，原生外部注册队列不能使用此入口。
     *
     * @param queueName 队列名称
     * @param data 消息
     * @return 消费完成或失败确认
     */
    public CompletableFuture<Void> sendAsync(String queueName, Object data) {
        return engine.send(queueName, data);
    }

    /**
     * 保留原动态建队列参数，调用方线程工厂必须返回未启动线程。
     *
     * @param queueName 名称
     * @param bufferSize 容量
     * @param producerType 生产者模式
     * @param waitStrategy 等待策略
     * @param threadFactory 线程工厂
     * @param handler 原事件消费者
     * @param <T> 消息类型
     * @return 已启动队列
     */
    @SuppressWarnings("unchecked")
    public <T> Disruptor<DisruptorEvent<T>> createQueue(String queueName, int bufferSize, ProducerType producerType,
                                                       WaitStrategy waitStrategy, ThreadFactory threadFactory,
                                                       MessageHandler<T> handler) {
        return engine.createQueue(queueName, bufferSize, producerType, waitStrategy, threadFactory, 1,
                (Class<T>) Object.class, new MessageHandlerAdapter<>(handler));
    }

    /**
     * 供注解处理器使用的类型化多工作线程入口。
     *
     * @param queueName 名称
     * @param bufferSize 容量
     * @param producerType 生产者模式
     * @param waitStrategy 等待策略
     * @param threadFactory 线程工厂
     * @param workers 工作线程数
     * @param type 消息类型
     * @param handler LMAX 处理器
     * @param <T> 消息类型
     * @return 已启动队列
     */
    public <T> Disruptor<DisruptorEvent<T>> createListenerQueue(String queueName, int bufferSize, ProducerType producerType,
                                                               WaitStrategy waitStrategy, ThreadFactory threadFactory,
                                                               int workers, Class<T> type, EventHandler<DisruptorEvent<T>> handler) {
        return engine.createQueue(queueName, bufferSize, producerType, waitStrategy, threadFactory, workers, type, handler);
    }

    /**
     * 保留原生实例注册，调用方须先配置并启动，注册后由模板执行停止。
     *
     * @param queueName 名称
     * @param disruptor 原生实例
     */
    public void registerDisruptor(String queueName, Disruptor<DisruptorEvent<Object>> disruptor) {
        engine.registerDisruptor(queueName, disruptor);
    }

    /**
     * 保留原手工指标入口，同一实例重复登记是幂等的。
     *
     * @param queueName 名称
     * @param disruptor 对应实例
     */
    public void registerMetrics(String queueName, Disruptor<DisruptorEvent<Object>> disruptor) {
        engine.registerMetrics(queueName, disruptor);
    }

    /**
     * 关闭指定队列，之后允许同名重建。
     *
     * @param queueName 名称
     */
    public void shutdown(String queueName) {
        engine.shutdown(queueName);
    }

    /**
     * 关闭当前全部队列，但原模板仍可继续创建新队列。
     */
    public void shutdownAll() {
        engine.shutdownAll();
    }

    /**
     * 查询名称快照。
     *
     * @return 当前名称
     */
    public Set<String> names() {
        return engine.names();
    }

    /**
     * 查询不包含业务内容的统计。
     *
     * @param name 名称
     * @return 统计快照
     */
    public Map<String, Object> stats(String name) {
        return engine.stats(name);
    }

    /**
     * 直接构造的模板负责最终关闭，注入的共享引擎由其提供者关闭。
     */
    @Override
    public void close() {
        if (owned) {
            engine.close();
        }
    }
}
