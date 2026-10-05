package io.github.bytex0.docs.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文档示例(DocsControllerTest)真实文档生成与认证测试
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
@SpringBootTest(properties = {"swagger.username=test-user", "swagger.password=test-pass"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DocsControllerTest {

    /**
     * 实际 Spring MVC 测试入口
     */
    private final MockMvc mvc;

    DocsControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void shouldProtectJsonYamlAndUi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs").header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldGenerateOpenApiWithValidAuthentication() throws Exception {
        String credentials = Base64.getEncoder().encodeToString("test-user:test-pass".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/v3/api-docs").header("Authorization", "Basic " + credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.info.title").value("Common Tool Docs"))
                .andExpect(jsonPath("$.paths['/api/docs/ping'].get.summary").value("示例连通性检查"));
        mvc.perform(get("/api/docs/ping")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
}
