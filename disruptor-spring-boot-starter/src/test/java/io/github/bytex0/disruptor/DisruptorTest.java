package io.github.bytex0.disruptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 顺序消费、失败确认、容量及生命周期回归。
 *
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
class DisruptorTest {

    /**
     * 测试消费者，使用公开接口定义行为。
     *
     * @author bytex0
     * @since 2026-10-05 19:39:53
     */
    private record Handler(Consumer<String> action) implements MessageHandler<String> {
        public String name() { return "test"; }
        public Class<String> type() { return String.class; }
        public void handle(String message) { action.accept(message); }
    }

    @Test
    void autoConfigurationSupportsDisableAndOverride() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DisruptorAutoConfiguration.class));
        runner.run(context -> assertThat(context.getBean(DisruptorTemplate.class).isRunning()).isTrue());
        runner.withPropertyValues("disruptor.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(DisruptorTemplate.class));
        DisruptorTemplate custom = new DisruptorTemplate(List.of(), 2);
        runner.withBean(DisruptorTemplate.class, () -> custom).run(context ->
                assertThat(context.getBean(DisruptorTemplate.class)).isSameAs(custom));
        runner.withPropertyValues("disruptor.buffer-size=3").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void confirmsConsumptionAndRecoversAfterHandlerFailure() throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        Handler handler = new Handler(message -> {
            if (message.equals("fail")) { throw new IllegalStateException("business failure"); }
            messages.add(message);
        });
        try (DisruptorTemplate template = new DisruptorTemplate(List.of(handler), 4)) {
            template.start();
            template.send("test", "first").get(2, TimeUnit.SECONDS);
            assertThatThrownBy(() -> template.send("test", "fail").get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
            template.send("test", "last").get(2, TimeUnit.SECONDS);
            assertThat(messages).containsExactly("first", "last");
            assertThatIllegalArgumentException().isThrownBy(() -> template.send("test", 123));
        }
    }

    @Test
    void rejectsDuplicatesFullQueueAndSendsAfterShutdown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Handler handler = new Handler(message -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        assertThatIllegalArgumentException().isThrownBy(() -> new DisruptorTemplate(List.of(handler, handler), 2));
        DisruptorTemplate template = new DisruptorTemplate(List.of(handler), 2);
        try {
            template.start();
            template.send("test", "one");
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            template.send("test", "two");
            assertThatThrownBy(() -> template.send("test", "three")).isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            template.close();
        }
        assertThatThrownBy(() -> template.send("test", "closed")).isInstanceOf(RejectedExecutionException.class);
        assertThat(template.isRunning()).isFalse();
    }
}
