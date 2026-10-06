package io.github.bytex0.desensitize;

import io.github.bytex0.desensitize.config.DesensitizeAutoConfiguration;
import io.github.bytex0.desensitize.fastjson.DesensitizeFastjson2ValueFilter;
import io.github.bytex0.desensitize.fastjson.DesensitizeValueFilter;
import io.github.bytex0.desensitize.jackson.DesensitizeModule;
import io.github.bytex0.desensitize.util.DesensitizeUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置测试(DesensitizeConfigurationTest)验证独立开关、可选依赖和实例工具的五个原入口。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
class DesensitizeConfigurationTest {

    /**
     * 最小 Boot 4 配置，不引入中间件。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DesensitizeAutoConfiguration.class, JacksonAutoConfiguration.class));

    /**
     * 总开关及 Fastjson 子开关都生效。
     */
    @Test
    void shouldEnableBothFastjsonApisOnlyWhenRequested() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DesensitizeValueFilter.class));
        runner.withPropertyValues("desensitize.enable-fastjson=true").run(context -> assertThat(context)
                .hasSingleBean(DesensitizeValueFilter.class).hasSingleBean(DesensitizeFastjson2ValueFilter.class));
        runner.withPropertyValues("desensitize.enabled=false", "desensitize.enable-fastjson=true")
                .run(context -> assertThat(context).doesNotHaveBean(DesensitizeValueFilter.class)
                        .doesNotHaveBean(DesensitizeUtil.class));
        runner.withPropertyValues("desensitize.enable-mybatis=true")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 没有 Fastjson 时主能力正常，只有原生 Fastjson 2 时也可独立装配。
     */
    @Test
    void shouldIsolateOptionalFastjsonClasses() {
        runner.withClassLoader(new FilteredClassLoader("com.alibaba.fastjson", "com.alibaba.fastjson2"))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DesensitizeModule.class));
        runner.withPropertyValues("desensitize.enable-fastjson=true")
                .withClassLoader(new FilteredClassLoader(name -> name.startsWith("com.alibaba.fastjson.")))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DesensitizeFastjson2ValueFilter.class));
    }

    /**
     * 关闭全局 Jackson 模块后显式工具仍可脱敏，转换入口不共享静态状态。
     */
    @Test
    void shouldKeepExplicitUtilityIndependentOfGlobalJacksonSwitch() {
        runner.withPropertyValues("desensitize.enable-jackson=false").run(context -> {
            assertThat(context).doesNotHaveBean(DesensitizeModule.class);
            JsonMapper global = context.getBean(JsonMapper.class);
            assertThat(global.writeValueAsString(new SerializationCompatibilityTest.Profile())).contains("13800138000");
            DesensitizeUtil util = context.getBean(DesensitizeUtil.class);
            String json = util.toJson(new SerializationCompatibilityTest.Profile());
            assertThat(json).doesNotContain("13800138000");
            assertThat(util.toObject(json, Map.class)).containsEntry("phone", "138****8000");
            List<Map<String, Object>> list = util.toObject("[" + json + "]", new TypeReference<>() { });
            assertThat(list.getFirst()).containsEntry("phone", "138****8000");
            assertThat(util.convertObject(new SerializationCompatibilityTest.Profile(), Map.class))
                    .containsEntry("phone", "138****8000");
            List<Map<String, Object>> converted = util.convertObject(
                    List.of(new SerializationCompatibilityTest.Profile()), new TypeReference<>() { });
            assertThat(converted.getFirst()).containsEntry("phone", "138****8000");
            Map<String, Object> nullable = new LinkedHashMap<>();
            nullable.put("missing", null);
            assertThat(util.toJson(nullable)).isEqualTo("{}");
            assertThatThrownBy(() -> util.toObject("{invalid", Map.class)).isInstanceOf(RuntimeException.class);
        });
    }
}
