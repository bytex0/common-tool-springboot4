package io.github.bytex0.common.log;

import io.github.bytex0.common.log.annotation.EnableOperationLog;
import io.github.bytex0.common.log.annotation.OperationLog;
import io.github.bytex0.common.log.annotation.OperationType;
import io.github.bytex0.common.log.aspect.OperationLogAspect;
import io.github.bytex0.common.log.config.OperationLogActivationAutoConfiguration;
import io.github.bytex0.common.log.config.OperationLogProperties;
import io.github.bytex0.common.log.model.OperationLogInfo;
import io.github.bytex0.common.log.service.OperationLogHandler;
import io.github.bytex0.common.log.service.OperationLogSanitizer;
import io.github.bytex0.common.log.service.OperatorInfoProvider;
import io.github.bytex0.common.log.service.WebOperationRequestAccessor;
import io.github.bytex0.exception.BizException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 操作日志启用、覆盖、脱敏、故障隔离和递归防护测试。
 *
 * @author bytex0
 * @since 2026-10-06 19:05:24
 */
class OperationLogTest {

    /**
     * 解析捕获 JSON 的测试映射器。
     */
    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * 属性激活入口，默认不开启日志。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OperationLogActivationAutoConfiguration.class));

    /**
     * 验证代理头默认不受信任，显式开启后保留原请求地址提取能力。
     */
    @Test
    void proxyHeadersRequireExplicitTrust() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "198.51.100.2, 192.0.2.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            OperationLogInfo safe = new OperationLogInfo();
            new WebOperationRequestAccessor(1024).populate(safe);
            assertThat(safe.getRequestIp()).isEqualTo("192.0.2.1");
            OperationLogInfo trusted = new OperationLogInfo();
            new WebOperationRequestAccessor(1024, true).populate(trusted);
            assertThat(trusted.getRequestIp()).isEqualTo("198.51.100.2");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
    /**
     * 验证注解启用、用户 Bean 优先、敏感字段和日志处理器递归保护。
     */
    @Test
    void capturesSanitizedPayloadAndUsesUserBeans() {
        runner.withUserConfiguration(UserConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(OperationLogHandler.class);
            AuditService service = context.getBean(AuditService.class);
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            Map<String, String> input = Map.of("name", "visible", "password", "fixture-secret");
            assertThat(service.echo(input)).isSameAs(input);
            assertThat(handler.logs()).hasSize(1);
            OperationLogInfo log = handler.logs().getFirst();
            assertThat(log.getOperationType()).isEqualTo(OperationType.UPDATE);
            assertThat(log.getOperatorId()).isEqualTo("bytex0");
            assertThat(log.getStatus()).isZero();
            assertThat(log.getDuration()).isGreaterThanOrEqualTo(0);
            JsonNode request = mapper.readTree(log.getRequestParams());
            assertThat(request.get(0).path("password").asString()).isEqualTo("***");
            assertThat(mapper.readTree(log.getResponseData()).path("name").asString()).isEqualTo("visible");
            assertThat(log.getResponseData()).doesNotContain("fixture-secret");
            assertThat(handler.convertLog(log)).isSameAs(log);
            service.noPayload("fixture-secret");
            OperationLogInfo suppressed = handler.logs().getLast();
            assertThat(suppressed.getRequestParams()).isNull();
            assertThat(suppressed.getResponseData()).isNull();
        });
    }

    /**
     * 验证日志处理器失败不替换业务成功结果或原始异常对象。
     */
    @Test
    void handlerFailureCannotReplaceBusinessOutcome() {
        runner.withUserConfiguration(UserConfiguration.class).run(context -> {
            AuditService service = context.getBean(AuditService.class);
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            handler.fail(true);
            assertThat(service.echo(Map.of("value", "ok"))).containsEntry("value", "ok");
            BizException failure = service.failureReference();
            assertThatThrownBy(service::fail).isSameAs(failure)
                    .satisfies(exception -> assertThat(exception.getSuppressed()).hasSize(1));
            assertThat(handler.logs().getLast().getStatus()).isEqualTo(1);
            assertThatThrownBy(service::error).isInstanceOf(AssertionError.class);
            assertThat(handler.logs().getLast().getErrorMessage()).isEqualTo("AssertionError");
        });
    }

    /**
     * 验证属性方式、独立关闭、非 Web 隔离和注解指定类型。
     */
    @Test
    void supportsActivationModesAndAnnotationTypes() {
        runner.withUserConfiguration(ServiceConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(OperationLogAspect.class));
        runner.withUserConfiguration(ServiceConfiguration.class)
                .withPropertyValues("common-tool.operation-log.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OperationLogAspect.class));
        runner.withUserConfiguration(UserConfiguration.class)
                .withPropertyValues("common-tool.operation-log.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OperationLogAspect.class));
        runner.withUserConfiguration(CustomTypeConfiguration.class)
                .withPropertyValues("common-tool.operation-log.enabled=true").run(context -> {
                    assertThat(context).hasSingleBean(RecordingHandler.class);
                    assertThat(context).hasSingleBean(NamedProvider.class);
                });
        runner.withUserConfiguration(UserConfiguration.class)
                .withClassLoader(new FilteredClassLoader("jakarta.servlet", "org.springframework.web"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(AuditService.class).echo(Map.of("value", "ok"));
                    assertThat(context.getBean(RecordingHandler.class).logs().getFirst().getRequestUri()).isNull();
                });
    }

    /**
     * 验证配置无效和注解指定处理器构造失败不能静默降级。
     */
    @Test
    void rejectsBrokenConfigurationAndHandlerCreation() {
        runner.withUserConfiguration(UserConfiguration.class)
                .withPropertyValues("common-tool.operation-log.max-payload-length=1")
                .run(context -> assertThat(context).hasFailed());
        runner.withUserConfiguration(BrokenConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 验证序列化超限、循环对象和内置敏感规则不会泄露数据。
     */
    @Test
    void captureLimitsDoNotMaterializeUnlimitedJson() {
        OperationLogProperties properties = new OperationLogProperties();
        properties.setMaxPayloadLength(128);
        OperationLogSanitizer sanitizer = new OperationLogSanitizer(properties);
        assertThat(mapper.readTree(sanitizer.capture("x".repeat(10000))).path("_capture").asString())
                .isEqualTo("omitted");
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("cycle", cycle);
        assertThat(mapper.readTree(sanitizer.capture(cycle)).path("_capture").asString()).isEqualTo("omitted");
        assertThat(sanitizer.capture(Map.of("access_token", "fixture-secret"))).doesNotContain("fixture-secret");
        JsonNode aliases = mapper.readTree(sanitizer.capture(Map.of("pwd", "fixture-secret", "auth", "fixture-secret")));
        assertThat(aliases.path("pwd").asString()).isEqualTo("***");
        assertThat(aliases.path("auth").asString()).isEqualTo("***");
    }

    /**
     * 用户 Bean 优先的注解配置。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    @Configuration(proxyBeanMethods = false)
    @EnableOperationLog
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class UserConfiguration {

        /**
         * 创建记录处理器。
         *
         * @return 处理器
         */
        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }

        /**
         * 创建用户身份提供者。
         *
         * @return 提供者
         */
        @Bean
        NamedProvider namedProvider() {
            return new NamedProvider();
        }

        /**
         * 创建测试业务。
         *
         * @return 业务服务
         */
        @Bean
        AuditService auditService() {
            return new AuditService();
        }
    }

    /**
     * 只声明业务 Bean，用于测试属性方式。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    @Configuration(proxyBeanMethods = false)
    static class ServiceConfiguration {

        /**
         * 创建测试业务。
         *
         * @return 业务服务
         */
        @Bean
        AuditService auditService() {
            return new AuditService();
        }
    }

    /**
     * 通过注解类型选择处理器和提供者。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    @Configuration(proxyBeanMethods = false)
    @EnableOperationLog(handler = RecordingHandler.class, provider = NamedProvider.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CustomTypeConfiguration {
    }

    /**
     * 构造失败处理器配置。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    @Configuration(proxyBeanMethods = false)
    @EnableOperationLog(handler = BrokenHandler.class)
    static class BrokenConfiguration {
    }

    /**
     * 可注入失败的记录处理器，本身也带日志注解以验证递归保护。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    public static class RecordingHandler implements OperationLogHandler {

        /**
         * 当前实例收到的日志。
         */
        private final List<OperationLogInfo> records = new CopyOnWriteArrayList<>();

        /**
         * 是否模拟写入失败。
         */
        private volatile boolean failing;

        /**
         * 记录日志后按配置抛出故障。
         *
         * @param info 日志模型
         */
        @Override
        @OperationLog
        public void handleLog(OperationLogInfo info) {
            records.add(info);
            if (failing) {
                throw new IllegalStateException("fixture handler failure");
            }
        }

        /**
         * 获取独立日志快照。
         *
         * @return 记录快照
         */
        public List<OperationLogInfo> logs() {
            return List.copyOf(records);
        }

        /**
         * 设置故障模式。
         *
         * @param value 是否失败
         */
        public void fail(boolean value) {
            failing = value;
        }
    }

    /**
     * 固定测试操作者。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    public static class NamedProvider implements OperatorInfoProvider {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getOperatorId() {
            return "bytex0";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getOperatorName() {
            return "fixture-user";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getOperatorDeptId() {
            return "fixture-dept";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getOperatorDeptName() {
            return "fixture-team";
        }
    }

    /**
     * 无法构造的处理器，用于验证失败不被默认实现掩盖。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    public static class BrokenHandler implements OperationLogHandler {

        /**
         * 模拟构造失败。
         */
        public BrokenHandler() {
            throw new IllegalStateException("fixture constructor failure");
        }

        /**
         * 不应执行的处理入口。
         *
         * @param info 日志模型
         */
        @Override
        public void handleLog(OperationLogInfo info) {
            throw new AssertionError("unreachable");
        }
    }

    /**
     * 覆盖成功、业务异常、Error 和关闭正文采集的业务服务。
     *
     * @author bytex0
     * @since 2026-10-06 19:05:24
     */
    public static class AuditService {

        /**
         * 固定异常用于验证对象身份。
         */
        private final BizException failure = new BizException("fixture business failure");

        /**
         * 返回原输入对象。
         *
         * @param input 输入对象
         * @return 同一对象
         */
        @OperationLog(type = OperationType.UPDATE, description = "fixture update")
        public Map<String, String> echo(Map<String, String> input) {
            return input;
        }

        /**
         * 关闭正文采集但保留业务返回值。
         *
         * @param input 输入
         * @return 原输入
         */
        @OperationLog(saveRequestData = false, saveResponseData = false)
        public String noPayload(String input) {
            return input;
        }

        /**
         * 获取原异常实例。
         *
         * @return 异常实例
         */
        public BizException failureReference() {
            return failure;
        }

        /**
         * 抛出业务异常。
         */
        @OperationLog
        public void fail() {
            throw failure;
        }

        /**
         * 抛出 Error，必须记录失败且向调用方保留原类型。
         */
        @OperationLog
        public void error() {
            throw new AssertionError("fixture error");
        }
    }
}
