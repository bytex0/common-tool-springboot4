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
     * 验证独立管理端口使用自己的地址、协议和路径，而不复用业务访问地址。
     *
     * @param output 捕获输出
     */
    @Test
    void shouldUseSeparateManagementAddress(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("local.server.port", "8080")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.servlet.context-path", "/app")
                .withProperty("local.management.port", "9443")
                .withProperty("management.server.address", "::1")
                .withProperty("management.server.ssl.enabled", "true")
                .withProperty("management.server.base-path", "/admin")
                .withProperty("management.endpoints.web.base-path", "/status");
        publishReadyEvent(environment, environment);
        assertThat(output).contains("http://127.0.0.1:8080/app/get-version")
                .contains("https://[::1]:9443/admin/status/health")
                .contains("https://[::1]:9443/admin/status/prometheus");
    }

    /**
     * 验证共享业务端口不会因独立管理服务器配置而打印不存在的 HTTPS 地址。
     *
     * @param output 捕获输出
     */
    @Test
    void shouldKeepSharedPortProtocol(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("local.server.port", "8080")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("management.server.ssl.enabled", "true");
        publishReadyEvent(environment, environment);
        assertThat(output).contains("http://127.0.0.1:8080/actuator/health")
                .doesNotContain("https://");
    }

    /**
     * 验证实际端口、HTTPS、IPv6 和上下文路径输出。
     *
     * @param output 捕获输出
     */
    @Test
    void shouldUseActualPortAndIpv6Urls(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "0")
                .withProperty("local.server.port", "8443")
                .withProperty("server.address", "::1")
                .withProperty("server.ssl.enabled", "true")
                .withProperty("server.servlet.context-path", "/demo");
        publishReadyEvent(environment, environment);
        assertThat(output).contains("https://[::1]:8443/demo/get-version")
                .contains("https://[::1]:8443/demo/actuator/health")
                .contains("https://[::1]:8443/demo/doc.html");
    }

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
