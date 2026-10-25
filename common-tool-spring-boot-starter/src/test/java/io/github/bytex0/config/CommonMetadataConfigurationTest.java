package io.github.bytex0.config;

import io.github.bytex0.controller.GitInfoController;
import io.github.bytex0.controller.VersionController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 元数据接口显式开关、非 Web 隔离与用户覆盖测试。
 *
 * @author bytex0
 * @since 2026-10-06 18:05:07
 */
class CommonMetadataConfigurationTest {

    /**
     * 仅加载元数据自动配置的 Web 上下文。
     */
    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonMetadataAutoConfiguration.class));

    /**
     * 验证默认关闭、非 Web 禁用及基础总开关优先。
     */
    @Test
    void requiresExplicitWebOptIn() {
        web.run(context -> assertThat(context).doesNotHaveBean(VersionController.class));
        web.withPropertyValues("common-tool.metadata.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(VersionController.class);
            assertThat(context).hasSingleBean(GitInfoController.class);
        });
        web.withPropertyValues("common-tool.metadata.enabled=true", "common-tool.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(VersionController.class));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CommonMetadataAutoConfiguration.class))
                .withPropertyValues("common-tool.metadata.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(VersionController.class));
    }

    /**
     * 验证用户 Bean 覆盖和无效资源容量拒绝。
     */
    @Test
    void respectsUserBeanAndValidatesCapacity() {
        VersionController custom = mock(VersionController.class);
        web.withPropertyValues("common-tool.metadata.enabled=true").withBean(VersionController.class, () -> custom)
                .run(context -> assertThat(context.getBean(VersionController.class)).isSameAs(custom));
        web.withPropertyValues("common-tool.metadata.enabled=true", "common-tool.metadata.max-resource-bytes=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
