package io.github.bytex0;

import io.github.bytex0.config.CommonToolProperties;
import io.github.bytex0.core.ApplicationInfoInitialize;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用工具自动配置(CommonToolConfigurationTest)测试
 *
 * @author linshiqiang
 * @since 2026-10-05 14:29:12
 */
class CommonToolConfigurationTest {

    /**
     * 不依赖 Web 容器与外部中间件的测试上下文
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonToolConfiguration.class));

    @Test
    void shouldRegisterAutoConfigurationImport() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(CommonToolConfiguration.class.getName());
    }

    @Test
    void shouldEnableInfrastructureByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CommonToolProperties.class);
            assertThat(context).hasSingleBean(ApplicationInfoInitialize.class);
            CommonToolProperties properties = context.getBean(CommonToolProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isApplicationInfoEnabled()).isTrue();
        });
    }

    @Test
    void shouldDisableAllInfrastructure() {
        contextRunner.withPropertyValues("common-tool.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CommonToolConfiguration.class);
            assertThat(context).doesNotHaveBean(CommonToolProperties.class);
            assertThat(context).doesNotHaveBean(ApplicationInfoInitialize.class);
        });
    }

    @Test
    void shouldDisableOnlyApplicationInfo() {
        contextRunner.withPropertyValues("common-tool.application-info-enabled=false").run(context -> {
            assertThat(context).hasSingleBean(CommonToolProperties.class);
            assertThat(context).doesNotHaveBean(ApplicationInfoInitialize.class);
            assertThat(context.getBean(CommonToolProperties.class).isApplicationInfoEnabled()).isFalse();
        });
    }

    @Test
    void shouldBackOffForUserDefinedBean() {
        ApplicationInfoInitialize custom = new ApplicationInfoInitialize(new MockEnvironment());
        contextRunner.withBean(ApplicationInfoInitialize.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(ApplicationInfoInitialize.class);
            assertThat(context.getBean(ApplicationInfoInitialize.class)).isSameAs(custom);
        });
    }
}
