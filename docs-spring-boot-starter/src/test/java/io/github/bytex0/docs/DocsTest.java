package io.github.bytex0.docs;

import io.github.bytex0.docs.config.DocsAccessFilter;
import io.github.bytex0.docs.config.SwaggerAutoConfiguration;
import io.github.bytex0.docs.properties.SwaggerProperties;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口文档(DocsTest)开关、用户覆盖及认证边界测试
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
class DocsTest {

    /**
     * Servlet 自动配置上下文
     */
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SwaggerAutoConfiguration.class));

    @Test
    void shouldConfigureMetadataAndRespectUserBean() {
        runner.withPropertyValues("swagger.title=custom").run(context ->
                assertThat(context.getBean(OpenAPI.class).getInfo().getTitle()).isEqualTo("custom"));
        OpenAPI custom = new OpenAPI();
        runner.withBean(OpenAPI.class, () -> custom).run(context ->
                assertThat(context.getBean(OpenAPI.class)).isSameAs(custom));
    }

    @Test
    void shouldKeepGuardWhenDocumentationDisabled() {
        runner.withPropertyValues("swagger.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(OpenAPI.class).hasSingleBean(DocsAccessFilter.class));
    }

    @Test
    void shouldRequireExplicitCredentials() {
        SwaggerProperties properties = new SwaggerProperties();
        properties.setBasicAuth(true);
        assertThatThrownBy(() -> new DocsAccessFilter(properties, new MockEnvironment()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldProtectCustomPathsAndServletPrefix() throws Exception {
        SwaggerProperties properties = new SwaggerProperties();
        properties.setBasicAuth(true);
        properties.setUsername("test-user");
        properties.setPassword("test-pass");
        DocsAccessFilter filter = new DocsAccessFilter(properties, new MockEnvironment()
                .withProperty("spring.mvc.servlet.path", "/app").withProperty("springdoc.api-docs.path", "/openapi"));
        for (String path : new String[]{"/app/openapi", "/app/openapi.yaml", "/app/openapi/group",
                "/app/swagger-ui/index.html", "/app/swagger-ui/index.html;session=x"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void shouldDenyDisabledDocsAndAllowBusinessRoutes() throws Exception {
        SwaggerProperties properties = new SwaggerProperties();
        properties.setEnabled(false);
        DocsAccessFilter filter = new DocsAccessFilter(properties, new MockEnvironment());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/v3/api-docs"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(404);
        MockFilterChain business = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/docs/ping"), new MockHttpServletResponse(), business);
        assertThat(business.getRequest()).isNotNull();
    }
}
