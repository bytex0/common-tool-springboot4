package io.github.bytex0.docs;

import io.github.bytex0.docs.config.DocsAccessFilter;
import io.github.bytex0.docs.config.SwaggerAutoConfiguration;
import io.github.bytex0.docs.properties.SwaggerProperties;
import io.swagger.v3.oas.models.OpenAPI;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.beans.Introspector;
import java.util.Arrays;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.CorsFilter;
import org.springframework.http.HttpHeaders;

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

    /**
     * 保留元信息设置并让用户 OpenAPI Bean 优先。
     */
    @Test
    void shouldConfigureMetadataAndRespectUserBean() {
        runner.withPropertyValues("swagger.title=custom").run(context ->
                assertThat(context.getBean(OpenAPI.class).getInfo().getTitle()).isEqualTo("custom"));
        OpenAPI custom = new OpenAPI();
        runner.withBean(OpenAPI.class, () -> custom).run(context ->
                assertThat(context.getBean(OpenAPI.class)).isSameAs(custom));
    }

    /**
     * 禁用后保留访问保护，防止第三方默认端点仍公开文档。
     */
    @Test
    void shouldKeepGuardWhenDocumentationDisabled() {
        runner.withPropertyValues("swagger.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(OpenAPI.class).hasSingleBean(DocsAccessFilter.class));
    }

    /**
     * 不继承旧组件的隐含默认用户名和密码。
     */
    @Test
    void shouldRequireExplicitCredentials() {
        SwaggerProperties properties = new SwaggerProperties();
        properties.setBasicAuth(true);
        assertThatThrownBy(() -> new DocsAccessFilter(properties, new MockEnvironment()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 自定义 API 路径及 Servlet 前缀仍受认证保护。
     *
     * @throws Exception 过滤器执行失败
     */
    @Test
    void shouldProtectCustomPathsAndServletPrefix() throws Exception {
        SwaggerProperties properties = new SwaggerProperties();
        properties.setBasicAuth(true);
        properties.setUsername("test-user");
        properties.setPassword("test-pass");
        DocsAccessFilter filter = new DocsAccessFilter(properties, new MockEnvironment()
                .withProperty("spring.mvc.servlet.path", "/app").withProperty("springdoc.api-docs.path", "/openapi"));
        for (String path : new String[]{"/app/openapi", "/app/openapi.yaml", "/app/openapi/group",
                "/app/swagger-ui/index.html", "/app/swagger-ui/index.html;session=x", "/app/doc.html",
                "/app/./doc.html", "/app//doc.html"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    /**
     * 禁用文档不拦截业务请求。
     *
     * @throws Exception 过滤器执行失败
     */
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

    /**
     * 恢复 Boolean 属性的原访问器和原 Basic 安全声明，不把凭据输出到 toString。
     *
     * @throws Exception JavaBeans 内省失败
     */
    @Test
    void shouldPreservePropertiesAndSecurityScheme() throws Exception {
        SwaggerProperties properties = new SwaggerProperties();
        assertThat(properties.getEnabled()).isTrue();
        assertThat(properties.getBasicAuth()).isFalse();
        properties.setUsername("local-test-user");
        properties.setPassword("local-test-secret");
        assertThat(properties.toString()).doesNotContain("local-test-user", "local-test-secret");
        var enabled = Arrays.stream(Introspector.getBeanInfo(SwaggerProperties.class).getPropertyDescriptors())
                .filter(property -> property.getName().equals("enabled")).findFirst().orElseThrow();
        assertThat(enabled.getPropertyType()).isEqualTo(Boolean.class);
        assertThat(enabled.getWriteMethod()).isNotNull();
        runner.withPropertyValues("swagger.basic-auth=true", "swagger.username=test-user", "swagger.password=test-pass")
                .run(context -> assertThat(context.getBean(OpenAPI.class).getComponents()
                        .getSecuritySchemes().get("basicAuth").getScheme()).isEqualTo("basic"));
    }

    /**
     * 已创建过滤器不因可变配置对象被单独修改而意外关闭认证。
     *
     * @throws Exception 过滤器执行失败
     */
    @Test
    void shouldKeepValidatedSecuritySnapshot() throws Exception {
        SwaggerProperties properties = new SwaggerProperties();
        properties.setBasicAuth(true);
        properties.setUsername("test-user");
        properties.setPassword("test-pass");
        DocsAccessFilter filter = new DocsAccessFilter(properties, new MockEnvironment());
        properties.setBasicAuth(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/doc.html"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThatThrownBy(() -> new DocsAccessFilter(new SwaggerProperties(),
                new MockEnvironment().withProperty("knife4j.enable", "true")
                        .withProperty("knife4j.basic.enable", "true")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 用户自定义增强 Bean 不应被兼容处理器替换。
     */
    @Test
    void shouldPreserveUserCustomizer() {
        Knife4jOpenApiCustomizer custom =
                new Knife4jOpenApiCustomizer(new Knife4jProperties(), new SpringDocConfigProperties()) {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void customise(OpenAPI document) {
                        document.addExtension("x-user-customizer", true);
                    }
                };
        runner.withBean(Knife4jOpenApiCustomizer.class, () -> custom).run(context -> {
            assertThat(context.getBean(Knife4jOpenApiCustomizer.class)).isSameAs(custom);
            OpenAPI document = new OpenAPI();
            context.getBean(Knife4jOpenApiCustomizer.class).customise(document);
            assertThat(document.getExtensions()).containsEntry("x-user-customizer", true);
        });
    }

    /**
     * 不允许通过通配 Origin 打开带凭据的 CORS。
     */
    @Test
    void shouldRejectWildcardCredentialedCors() {
        runner.withConfiguration(AutoConfigurations.of(Knife4jAutoConfiguration.class))
                .withInitializer(context -> context.getBeanFactory()
                        .registerSingleton("testSpringDocProperties", new SpringDocConfigProperties()))
                .withPropertyValues("knife4j.enable=true", "knife4j.cors=true", "swagger.cors-allow-credentials=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    /**
     * 原 CORS 开关默认允许无凭据跨域，不再产生 Spring 的通配凭据异常。
     */
    @Test
    void shouldSupportDefaultCorsWithoutCredentials() {
        runner.withConfiguration(AutoConfigurations.of(Knife4jAutoConfiguration.class))
                .withInitializer(context -> context.getBeanFactory()
                        .registerSingleton("testSpringDocProperties", new SpringDocConfigProperties()))
                .withPropertyValues("knife4j.enable=true", "knife4j.cors=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/example");
                    request.addHeader(HttpHeaders.ORIGIN, "https://docs.example.test");
                    request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    context.getBean("knife4jCorsFilter", CorsFilter.class)
                            .doFilter(request, response, new MockFilterChain());
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
                    assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
                });
    }

    /**
     * 原 Knife4j Basic 配置也保护自定义 API 路径，冲突凭据不能静默并存。
     *
     * @throws Exception 过滤器执行失败
     */
    @Test
    void shouldSupportOriginalBasicConfiguration() throws Exception {
        SwaggerProperties properties = new SwaggerProperties();
        MockEnvironment environment = new MockEnvironment().withProperty("knife4j.enable", "true")
                .withProperty("knife4j.basic.enable", "true").withProperty("knife4j.basic.username", "test-user")
                .withProperty("knife4j.basic.password", "test-pass").withProperty("springdoc.api-docs.path", "/schema");
        DocsAccessFilter filter = new DocsAccessFilter(properties, environment);
        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/schema"), denied, new MockFilterChain());
        assertThat(denied.getStatus()).isEqualTo(401);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/schema");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                .encodeToString("test-user:test-pass".getBytes(StandardCharsets.UTF_8)));
        MockFilterChain allowed = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), allowed);
        assertThat(allowed.getRequest()).isNotNull();
        properties.setBasicAuth(true);
        properties.setUsername("different-user");
        properties.setPassword("different-pass");
        assertThatThrownBy(() -> new DocsAccessFilter(properties, environment))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
