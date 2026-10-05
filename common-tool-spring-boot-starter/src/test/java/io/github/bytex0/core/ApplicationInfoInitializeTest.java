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
 * @author linshiqiang
 * @since 2026-10-05 14:29:12
 */
@ExtendWith(OutputCaptureExtension.class)
class ApplicationInfoInitializeTest {

    @Test
    void shouldLogApplicationNameAndActiveProfiles(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "demo");
        environment.setActiveProfiles("test", "local");
        publishReadyEvent(environment, environment);
        assertThat(output).contains("Application ready: name=demo, profiles=test,local");
    }

    @Test
    void shouldUseDefaultNameAndProfile(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        publishReadyEvent(environment, environment);
        assertThat(output).contains("Application ready: name=application, profiles=default");
    }

    @Test
    void shouldIgnoreOtherApplicationContext(CapturedOutput output) {
        publishReadyEvent(new MockEnvironment(), new MockEnvironment());
        assertThat(output).doesNotContain("Application ready:");
    }

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
