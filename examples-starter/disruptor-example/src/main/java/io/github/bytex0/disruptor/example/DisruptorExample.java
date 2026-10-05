package io.github.bytex0.disruptor.example;

import io.github.bytex0.disruptor.DisruptorTemplate;
import io.github.bytex0.disruptor.MessageHandler;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用消费确认验证后台队列的独立示例。
 *
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
@SpringBootApplication
public class DisruptorExample {

    /**
     * 启动真实依赖队列 Starter 的示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DisruptorExample.class, args);
    }

    /**
     * 提供类型化业务处理器。
     *
     * @return 计数消费者
     */
    @Bean
    Counter handler() {
        return new Counter();
    }

    /**
     * 累加消费者，失败消息不更新状态。
     *
     * @author bytex0
     * @since 2026-10-05 19:39:53
     */
    static class Counter implements MessageHandler<Long> {

        /**
         * 已成功处理的累计值。
         */
        private final AtomicLong total = new AtomicLong();

        /**
         * {@inheritDoc}
         */
        public String name() {
            return "counter";
        }

        /**
         * {@inheritDoc}
         */
        public Class<Long> type() {
            return Long.class;
        }

        /**
         * {@inheritDoc}
         */
        public void handle(Long value) {
            if (value < 0) {
                throw new IllegalStateException("negative value");
            }
            total.addAndGet(value);
        }
    }

    /**
     * HTTP 接口等待实际消费完成后返回。
     *
     * @author bytex0
     * @since 2026-10-05 19:39:53
     */
    @RestController
    static class Controller {

        /**
         * 实际类型化模板。
         */
        private final DisruptorTemplate template;

        /**
         * 业务计数器。
         */
        private final Counter counter;

        /**
         * 注入实际队列与处理器。
         *
         * @param template 模板
         * @param counter 计数器
         */
        Controller(DisruptorTemplate template, Counter counter) {
            this.template = template;
            this.counter = counter;
        }

        /**
         * 等待指定队列实际消费后返回。
         *
         * @param queue 队列名称
         * @param value 业务值
         * @return 累计值
         * @throws Exception 消费失败或等待超时
         */
        @PostMapping("/api/disruptor/send")
        Map<String, Object> send(@RequestParam(defaultValue = "counter") String queue,
                                 @RequestParam long value) throws Exception {
            template.send(queue, value).get(3, TimeUnit.SECONDS);
            return Map.of("code", 0, "data", Map.of("total", counter.total.get()));
        }

        /**
         * 参数错误返回 400。
         *
         * @return 错误码
         */
        @ExceptionHandler(IllegalArgumentException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        Map<String, Integer> invalid() {
            return Map.of("code", 400);
        }

        /**
         * 消费失败返回 409。
         *
         * @return 错误码
         */
        @ExceptionHandler(ExecutionException.class)
        @ResponseStatus(HttpStatus.CONFLICT)
        Map<String, Integer> failed() {
            return Map.of("code", 409);
        }
    }
}
