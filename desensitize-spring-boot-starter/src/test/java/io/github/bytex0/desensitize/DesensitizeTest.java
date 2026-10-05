package io.github.bytex0.desensitize;

import io.github.bytex0.desensitize.annotation.Desensitize;
import io.github.bytex0.desensitize.config.DesensitizeAutoConfiguration;
import io.github.bytex0.desensitize.enums.DesensitizeType;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;
import io.github.bytex0.desensitize.jackson.DesensitizeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据脱敏(DesensitizeTest)字段隔离、完整策略及错误保护测试
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
class DesensitizeTest {

    /**
     * 无静态状态的策略工厂
     */
    private final DesensitizeHandlerFactory factory = new DesensitizeHandlerFactory(new DefaultListableBeanFactory());

    @Test
    void shouldConfigureDisableAndOverride() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DesensitizeAutoConfiguration.class, JacksonAutoConfiguration.class));
        runner.run(context -> assertThat(context).hasSingleBean(DesensitizeModule.class));
        runner.withPropertyValues("desensitize.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DesensitizeModule.class));
        runner.withBean(DesensitizeHandlerFactory.class, () -> factory)
                .run(context -> assertThat(context.getBean(DesensitizeHandlerFactory.class)).isSameAs(factory));
    }

    @Test
    void shouldIsolateFieldsAndLeaveSourceUntouched() throws Exception {
        JsonMapper mapper = JsonMapper.builder().addModule(new DesensitizeModule(factory)).build();
        Sample sample = new Sample();
        String expected = mapper.writeValueAsString(sample);
        assertThat(mapper.readTree(expected).path("phone").asString()).isEqualTo("138****8000");
        assertThat(mapper.readTree(expected).path("ordinary").asString()).isEqualTo("ordinary");
        assertThat(sample.phone).isEqualTo("13800138000");
        assertThat(mapper.writeValueAsString("raw")).isEqualTo("\"raw\"");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = executor.invokeAll(IntStream.range(0, 32)
                    .<Callable<String>>mapToObj(index -> () -> mapper.writeValueAsString(sample)).toList());
            for (var task : tasks) { assertThat(task.get()).isEqualTo(expected); }
        }
        assertThat(mapper.writeValueAsString(List.of(sample))).doesNotContain("13800138000");
    }

    @Test
    void shouldCoverPreviouslyMissingTypesAndMalformedValues() {
        for (DesensitizeType type : DesensitizeType.values()) {
            if (type != DesensitizeType.CUSTOM) {
                assertThat(factory.mask("sensitive", type)).isNotEqualTo("sensitive");
            }
        }
        assertThat(factory.mask("a@example.com", DesensitizeType.EMAIL)).isEqualTo("*@example.com");
        assertThat(factory.mask("bad-ip", DesensitizeType.IPV4)).isEqualTo("******");
    }

    @Test
    void shouldMaskUnicodeCodePointsAndIncludeLastCharacter() {
        assertThat(factory.maskRange("\uD801\uDC00AB", 1, -1, "#")).isEqualTo("\uD801\uDC00##");
        assertThatThrownBy(() -> factory.maskRange("ABC", 2, 1, "*")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonStringFieldsRatherThanExposePlaintext() {
        JsonMapper mapper = JsonMapper.builder().addModule(new DesensitizeModule(factory)).build();
        assertThatThrownBy(() -> mapper.writeValueAsString(new Invalid())).isInstanceOf(RuntimeException.class);
    }

    /**
     * 脱敏测试(Sample)不同字段规则
     *
     * @author linshiqiang
     * @since 2026-10-05 15:57:28
     */
    public static class Sample {

        /**
         * 合成手机号
         */
        @Desensitize(type = DesensitizeType.PHONE)
        public String phone = "13800138000";

        /**
         * 姓名
         */
        @Desensitize(type = DesensitizeType.NAME)
        public String name = "张三";

        /**
         * 普通文本
         */
        public String ordinary = "ordinary";
    }

    /**
     * 无效配置(Invalid)非字符串字段测试
     *
     * @author linshiqiang
     * @since 2026-10-05 15:57:28
     */
    public static class Invalid {

        /**
         * 不支持直接对数值序列化为脱敏字符串
         */
        @Desensitize(type = DesensitizeType.PHONE)
        public int phone = 123;
    }
}
