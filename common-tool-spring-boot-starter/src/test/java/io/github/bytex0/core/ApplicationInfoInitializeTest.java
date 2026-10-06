package io.github.bytex0.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用启动信息(ApplicationInfoInitializeTest)事件处理测试
 *
 * @author bytex0
 * @since 2026-10-05 14:29:12
 */
@ExtendWith(OutputCaptureExtension.class)
class ApplicationInfoInitializeTest {

    /**
     * 验证应用名称和激活环境输出。
     *
     * @param output 捕获输出
     */
    @Test
    void shouldLogApplicationNameAndActiveProfiles(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "demo");
        environment.setActiveProfiles("test", "local");
        publishReadyEvent(environment, environment);
        assertThat(output).contains("Application ready: name=demo, profiles=test,local");
    }

    /**
     * 验证默认应用名称和默认环境。
     *
     * @param output 捕获输出
     */
    @Test
    void shouldUseDefaultNameAndProfile(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        publishReadyEvent(environment, environment);
        assertThat(output).contains("Application ready: name=application, profiles=default");
    }

    /**
     * 验证其他应用上下文事件不会重复输出。
     *
     * @param output 捕获输出
     */
    @Test
    void shouldIgnoreOtherApplicationContext(CapturedOutput output) {
        publishReadyEvent(new MockEnvironment(), new MockEnvironment());
        assertThat(output).doesNotContain("Application ready:");
    }

    /**
     * 构造应用就绪事件，结束后关闭测试上下文。
     *
     * @param listenerEnvironment 监听器所属环境
     * @param contextEnvironment 事件所属环境
     */
    private void publishReadyEvent(MockEnvironment listenerEnvironment, MockEnvironment contextEnvironment) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(contextEnvironment);
            context.refresh();
            ApplicationReadyEvent event = new ApplicationReadyEvent(
                    new SpringApplication(Object.class), new String[0], context, Duration.ZERO);
            new ApplicationInfoInitialize(listenerEnvironment).onApplicationEvent(event);
        }
    }
}
