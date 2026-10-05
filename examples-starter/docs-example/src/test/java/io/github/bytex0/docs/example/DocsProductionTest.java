package io.github.bytex0.docs.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 原 Knife4j 生产环境屏蔽功能在 Boot 4 下的回归。
 *
 * @author bytex0
 * @since 2026-10-05 23:59:15
 */
@SpringBootTest(classes = DocsExampleApplication.class,
        properties = {"knife4j.production=true", "swagger.basic-auth=false"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DocsProductionTest {

    /**
     * 真实 MVC 测试入口。
     */
    private final MockMvc mvc;

    /**
     * 注入请求执行器。
     *
     * @param mvc MVC 入口
     */
    DocsProductionTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 屏蔽文档但保留业务接口可达。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void shouldDisableDocumentationInProductionMode() throws Exception {
        for (String path : new String[]{"/doc.html", "/swagger-ui/index.html", "/v3/api-docs",
                "/v3/api-docs.yaml", "/v3/api-docs/sample"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/docs/ping")).andExpect(status().isOk());
    }
}
