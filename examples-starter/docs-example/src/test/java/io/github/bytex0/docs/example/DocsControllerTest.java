package io.github.bytex0.docs.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文档示例(DocsControllerTest)真实文档生成与认证测试
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
@SpringBootTest(classes = DocsExampleApplication.class,
        properties = {"swagger.username=test-user", "swagger.password=test-pass"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DocsControllerTest {

    /**
     * 实际 Spring MVC 测试入口
     */
    private final MockMvc mvc;

    /**
     * 注入实际 MVC 入口。
     *
     * @param mvc 测试请求执行器
     */
    DocsControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * JSON、YAML 和两种 UI 都不能绕过认证。
     *
     * @throws Exception MVC 请求失败
     */
    @Test
    void shouldProtectJsonYamlAndUi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/doc.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs").header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 真实 OpenAPI 包含原安全声明及 Knife4j 扩展，业务接口不受影响。
     *
     * @throws Exception MVC 请求失败
     */
    @Test
    void shouldGenerateOpenApiWithValidAuthentication() throws Exception {
        String credentials = Base64.getEncoder().encodeToString("test-user:test-pass".getBytes(StandardCharsets.UTF_8));
        String document = mvc.perform(get("/v3/api-docs").header("Authorization", "Basic " + credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.info.title").value("Common Tool Docs"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$['x-openapi']").exists())
                .andExpect(jsonPath("$.tags[0]['x-order']").value(7))
                .andExpect(jsonPath("$.paths['/api/docs/ping'].get['x-order']").value(3))
                .andExpect(jsonPath("$.paths['/api/docs/ping'].get.summary").value("示例连通性检查"))
                .andReturn().getResponse().getContentAsString();
        assertThat(document).contains("knife4j-boot4-markdown-fixture");
        mvc.perform(get("/doc.html").header("Authorization", "Basic " + credentials))
                .andExpect(status().isOk());
        mvc.perform(get("/api/docs/ping")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
}
