package io.github.bytex0;

import io.github.bytex0.config.CommonToolProperties;
import io.github.bytex0.core.ApplicationInfoInitialize;
import io.github.bytex0.id.IdWorkerUtil;
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
 * @author bytex0
 * @since 2026-10-05 14:29:12
 */
class CommonToolConfigurationTest {

    /**
     * 不依赖 Web 容器与外部中间件的测试上下文
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonToolConfiguration.class));

    /**
     * 验证 Boot 自动配置注册文件。
     */
    @Test
    void shouldRegisterAutoConfigurationImport() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(CommonToolConfiguration.class.getName());
    }

    /**
     * 验证默认基础 Bean 和配置值。
     */
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

    /**
     * 验证总开关关闭所有基础 Bean。
     */
    @Test
    void shouldDisableAllInfrastructure() {
        contextRunner.withPropertyValues("common-tool.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CommonToolConfiguration.class);
            assertThat(context).doesNotHaveBean(CommonToolProperties.class);
            assertThat(context).doesNotHaveBean(ApplicationInfoInitialize.class);
        });
    }

    /**
     * 验证独立关闭启动信息。
     */
    @Test
    void shouldDisableOnlyApplicationInfo() {
        contextRunner.withPropertyValues("common-tool.application-info-enabled=false").run(context -> {
            assertThat(context).hasSingleBean(CommonToolProperties.class);
            assertThat(context).doesNotHaveBean(ApplicationInfoInitialize.class);
            assertThat(context.getBean(CommonToolProperties.class).isApplicationInfoEnabled()).isFalse();
        });
    }

    /**
     * 验证用户提供的启动监听器优先。
     */
    @Test
    void shouldBackOffForUserDefinedBean() {
        ApplicationInfoInitialize custom = new ApplicationInfoInitialize(new MockEnvironment());
        contextRunner.withBean(ApplicationInfoInitialize.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(ApplicationInfoInitialize.class);
            assertThat(context.getBean(ApplicationInfoInitialize.class)).isSameAs(custom);
        });
    }

    /**
     * 验证 ID Bean 的节点配置、关闭和用户覆盖。
     */
    @Test
    void configuresIdWorkerAndRespectsOverrides() {
        contextRunner.withPropertyValues("common-tool.worker-id=5").run(context ->
                assertThat(context.getBean(IdWorkerUtil.class).nextId() >>> 53).isEqualTo(5));
        contextRunner.withPropertyValues("common-tool.id-enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(IdWorkerUtil.class));
        contextRunner.withPropertyValues("common-tool.worker-id=1024").run(context ->
                assertThat(context).hasFailed());
        IdWorkerUtil custom = new IdWorkerUtil(7L);
        contextRunner.withBean(IdWorkerUtil.class, () -> custom).run(context ->
                assertThat(context.getBean(IdWorkerUtil.class)).isSameAs(custom));
    }
}
