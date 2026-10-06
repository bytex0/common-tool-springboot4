package io.github.bytex0.script.config;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.executor.JavaScriptExecutor;
import io.github.bytex0.script.service.ScriptService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 类型化脚本服务的条件装配、配置绑定及用户 Bean 覆盖测试。
 *
 * @author bytex0
 * @since 2026-10-06 14:11:05
 */
class ScriptAutoConfigurationTest {

    /**
     * 每次运行创建独立上下文的测试工具。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScriptAutoConfiguration.class));

    /**
     * 验证默认关闭，显式启用后五语言可用。
     */
    @Test
    void optInCreatesAvailableLanguages() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ScriptService.class));
        runner.withPropertyValues("script.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ScriptService.class));
        runner.withPropertyValues("script.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ScriptService.class);
            assertThat(context.getBean(ScriptService.class).getSupportedTypes())
                    .containsExactlyInAnyOrder(ScriptType.values());
        });
    }

    /**
     * 验证可选依赖缺失时不加载对应执行器，不影响 Groovy 和 Java。
     */
    @Test
    void absentOptionalLanguagesDoNotBreakStartup() {
        runner.withPropertyValues("script.enabled=true")
                .withClassLoader(new FilteredClassLoader("org.graalvm", "org.luaj", "org.python"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ScriptService.class).getSupportedTypes())
                            .containsExactlyInAnyOrder(ScriptType.GROOVY, ScriptType.JAVA);
                });
    }

    /**
     * 验证语言关闭和旧属性对象字段绑定，缓存无效容量必须启动失败。
     */
    @Test
    void bindsPropertiesAndAppliesSwitches() {
        runner.withPropertyValues("script.enabled=true", "script.java-script.enabled=false",
                "script.lua.enabled=false", "script.python.enabled=false", "script.groovy.cache-size=3")
                .run(context -> {
                    assertThat(context.getBean(ScriptService.class).getSupportedTypes())
                            .containsExactlyInAnyOrder(ScriptType.GROOVY, ScriptType.JAVA);
                    assertThat(context.getBean(ScriptProperties.class).getGroovy().getCacheSize()).isEqualTo(3);
                });
        runner.withPropertyValues("script.enabled=true", "script.groovy.cache-size=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("script.enabled=true", "script.cache-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 验证用户服务和语言执行器均可覆盖默认 Bean。
     */
    @Test
    void respectsUserServiceAndExecutorOverrides() {
        ScriptService service = mock(ScriptService.class);
        runner.withPropertyValues("script.enabled=true").withBean(ScriptService.class, () -> service)
                .run(context -> assertThat(context.getBean(ScriptService.class)).isSameAs(service));
        JavaScriptExecutor executor = new JavaScriptExecutor(false);
        runner.withPropertyValues("script.enabled=true").withBean(JavaScriptExecutor.class, () -> executor)
                .run(context -> assertThat(context.getBean(ScriptService.class).getExecutor(ScriptType.JAVASCRIPT))
                        .isSameAs(executor));
    }
}
