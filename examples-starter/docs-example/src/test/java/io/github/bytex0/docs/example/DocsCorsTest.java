package io.github.bytex0.docs.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实过滤链中的 CORS 白名单、凭据和文档认证边界验证。
 *
 * @author bytex0
 * @since 2026-10-05 23:59:15
 */
@SpringBootTest(classes = DocsExampleApplication.class,
        properties = {"swagger.username=test-user", "swagger.password=test-pass", "knife4j.cors=true",
        "swagger.cors-allowed-origins[0]=https://docs.example.test", "swagger.cors-allow-credentials=true"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DocsCorsTest {

    /**
     * 真实 MVC 过滤链测试入口。
     */
    private final MockMvc mvc;

    /**
     * 注入测试入口。
     *
     * @param mvc MVC 请求执行器
     */
    DocsCorsTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 文档预检不要求携带 Basic 凭据，但实际文档请求仍须认证。
     *
     * @throws Exception 请求处理失败
     */
    @Test
    void shouldAllowPreflightWithoutBypassingAuthentication() throws Exception {
        mvc.perform(options("/v3/api-docs")
                        .header(HttpHeaders.ORIGIN, "https://docs.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://docs.example.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mvc.perform(get("/v3/api-docs").header(HttpHeaders.ORIGIN, "https://docs.example.test"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 不受信任 Origin 的业务预检被拒绝，不返回通配凭据许可。
     *
     * @throws Exception 请求处理失败
     */
    @Test
    void shouldRejectForeignOrigin() throws Exception {
        mvc.perform(options("/api/docs/ping")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
