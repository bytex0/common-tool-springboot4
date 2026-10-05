package io.github.bytex0.disruptor.example;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.github.bytex0.disruptor.annotation.WaitStrategyType;
import io.github.bytex0.disruptor.core.DisruptorEngine;
import io.github.bytex0.disruptor.event.DisruptorEvent;
import io.github.bytex0.disruptor.factory.DisruptorEventFactory;
import io.github.bytex0.disruptor.template.DisruptorTemplate;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 队列联调(QueueController)验证原动态接口、监听注解、容量拒绝及实际 Micrometer 指标。
 *
 * @author linshiqiang
 * @since 2026-10-06 03:35:33
 */
@RestController
@RequestMapping("/api/disruptor")
public class QueueController {

    /**
     * 示例动态队列最大容量。
     */
    private static final int MAX_EXAMPLE_BUFFER = 1024;

    /**
     * 示例每条消息最多延时一秒。
     */
    private static final long MAX_DELAY_MILLIS = 1000;

    /**
     * 类型化确认和统计入口。
     */
    private final DisruptorEngine engine;

    /**
     * 原包路径模板，实际共享同一个引擎。
     */
    private final DisruptorTemplate legacy;

    /**
     * 实际指标注册表。
     */
    private final MeterRegistry metrics;

    /**
     * 真实注解消费者。
     */
    private final AnnotatedConsumers listeners;

    /**
     * 动态队列共享的示例副作用计数。
     */
    private final AtomicLong dynamicTotal = new AtomicLong();

    /**
     * 原生注册队列真实处理次数。
     */
    private final AtomicLong rawProcessed = new AtomicLong();

    /**
     * 注入实际 Starter 组件，不复制队列实现。
     *
     * @param engine 共享引擎
     * @param legacy 原模板
     * @param metrics 指标
     * @param listeners 注解监听器
     */
    public QueueController(DisruptorEngine engine, DisruptorTemplate legacy, MeterRegistry metrics, AnnotatedConsumers listeners) {
        this.engine = engine;
        this.legacy = legacy;
        this.metrics = metrics;
        this.listeners = listeners;
    }

    /**
     * 通过原工厂创建动态队列，延时参数用于验证满队列。
     *
     * @param name 测试队列名
     * @param size 环容量
     * @param producer 生产者模式
     * @param wait 等待策略
     * @param virtual 是否虚拟线程
     * @param delay 每条消息延时毫秒数
     * @return 实际队列统计
     */
    @PostMapping("/queues/{name}")
    public Map<String, Object> create(@PathVariable String name, @RequestParam(defaultValue = "8") int size,
                                      @RequestParam(defaultValue = "MULTI") ProducerType producer,
                                      @RequestParam(defaultValue = "BLOCKING") WaitStrategyType wait,
                                      @RequestParam(defaultValue = "true") boolean virtual,
                                      @RequestParam(defaultValue = "0") long delay) {
        validate(name, size);
        Assert.isTrue(delay >= 0 && delay <= MAX_DELAY_MILLIS, "示例延时不合法");
        legacy.<Long>createQueue(name, size, producer, wait.create(), DisruptorEngine.threads(name, virtual), event -> {
            if (event.getData() < 0) {
                throw new IllegalStateException("negative value");
            }
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("consumer interrupted", exception);
                }
            }
            dynamicTotal.addAndGet(event.getData());
        });
        return stats(name);
    }

    /**
     * 通过原原生注册方法登记已经启动的 LMAX 实例，消费链由示例自己拥有。
     *
     * @param name 测试队列名称
     * @return 实际统计
     */
    @PostMapping("/queues/{name}/raw")
    public Map<String, Object> raw(@PathVariable String name) {
        validate(name, 8);
        Disruptor<DisruptorEvent<Object>> disruptor = new Disruptor<>(new DisruptorEventFactory<>(), 8,
                DisruptorEngine.threads(name, true), ProducerType.MULTI, WaitStrategyType.BLOCKING.create());
        disruptor.handleEventsWith((EventHandler<DisruptorEvent<Object>>) (event, sequence, end) -> {
            rawProcessed.incrementAndGet();
            event.clear();
        });
        disruptor.start();
        try {
            legacy.registerDisruptor(name, disruptor);
            legacy.registerMetrics(name, disruptor);
            return stats(name);
        } catch (RuntimeException failure) {
            disruptor.halt();
            throw failure;
        }
    }

    /**
     * 发送动态消息，legacy=true 仅确认入队，其余调用等待实际消费。
     *
     * @param name 测试队列
     * @param value 业务值
     * @param acceptedOnly 是否使用原 void 入口
     * @return 入队状态及副作用快照
     * @throws Exception 消费失败或超时
     */
    @PostMapping("/queues/{name}/send")
    public Map<String, Object> send(@PathVariable String name, @RequestParam long value,
                                    @RequestParam(name = "legacy", defaultValue = "false") boolean acceptedOnly) throws Exception {
        if (acceptedOnly) {
            legacy.send(name, value);
        } else {
            legacy.sendAsync(name, value).get(3, TimeUnit.SECONDS);
        }
        return Map.of("code", 0, "data", Map.of("accepted", true, "total", dynamicTotal.get(), "rawProcessed", rawProcessed.get()));
    }

    /**
     * 查询引擎统计和真实 Gauge，不返回内部队列对象。
     *
     * @param name 队列名称
     * @return 统计
     */
    @GetMapping("/queues/{name}")
    public Map<String, Object> stats(@PathVariable String name) {
        Map<String, Object> result = new LinkedHashMap<>(engine.stats(name));
        Gauge gauge = metrics.find("disruptor.buffer.size").tag("queue", name).gauge();
        result.put("metricPresent", gauge != null);
        result.put("metricCapacity", gauge == null ? 0 : gauge.value());
        result.put("rawProcessed", rawProcessed.get());
        return Map.of("code", 0, "data", result);
    }

    /**
     * 停止并注销动态队列。
     *
     * @param name 队列名
     * @return 当前名称
     */
    @DeleteMapping("/queues/{name}")
    public Map<String, Object> remove(@PathVariable String name) {
        validate(name, 2);
        legacy.shutdown(name);
        return Map.of("code", 0, "data", Map.of("names", engine.names()));
    }

    /**
     * 关闭后仍能单独验证指标是否被移除。
     *
     * @param name 队列名
     * @return 指标存在状态
     */
    @GetMapping("/metrics")
    public Map<String, Object> metric(@RequestParam String name) {
        return Map.of("code", 0, "data", Map.of("present",
                metrics.find("disruptor.buffer.size").tag("queue", name).gauge() != null));
    }

    /**
     * 向真实监听注解队列发送消息并等待实际回调。
     *
     * @param queue annotated 或 virtual
     * @param value 消息值
     * @return 监听器统计
     * @throws Exception 业务失败或等待超时
     */
    @PostMapping("/listener")
    public Map<String, Object> listener(@RequestParam(defaultValue = "annotated") String queue, @RequestParam long value)
            throws Exception {
        Assert.isTrue("annotated".equals(queue) || "virtual".equals(queue), "未知监听器");
        engine.send(queue, value).get(3, TimeUnit.SECONDS);
        return listenerStats();
    }

    /**
     * 查询监听器的真实副作用及观察到的线程模式。
     *
     * @return 监听统计
     */
    @GetMapping("/listeners")
    public Map<String, Object> listenerStats() {
        return Map.of("code", 0, "data", listeners.snapshot());
    }

    /**
     * 限定示例仅管理自己的动态命名空间，容量有明确上限。
     *
     * @param name 名称
     * @param size 容量
     */
    private void validate(String name, int size) {
        Assert.isTrue(name.matches("dynamic-[a-z0-9-]{1,48}"), "非法测试队列名称");
        Assert.isTrue(size >= 2 && size <= MAX_EXAMPLE_BUFFER && Integer.bitCount(size) == 1, "非法测试容量");
    }

    /**
     * 参数错误返回 400。
     *
     * @return 错误码
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Integer> invalid() {
        return Map.of("code", 400);
    }

    /**
     * 业务失败返回 409，不伪装成成功消费。
     *
     * @return 错误码
     */
    @ExceptionHandler(ExecutionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Integer> failed() {
        return Map.of("code", 409);
    }

    /**
     * 满队列返回 429。
     *
     * @return 错误码
     */
    @ExceptionHandler(RejectedExecutionException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Integer> rejected() {
        return Map.of("code", 429);
    }

    /**
     * 等待超时返回 504，不表示后台副作用一定没有执行。
     *
     * @return 错误码
     */
    @ExceptionHandler(TimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public Map<String, Integer> timedOut() {
        return Map.of("code", 504);
    }
}
