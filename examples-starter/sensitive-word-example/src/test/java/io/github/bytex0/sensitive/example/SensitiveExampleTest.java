package io.github.bytex0.sensitive.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 敏感词示例的完整 MVC 集成验证。
 *
 * @author bytex0
 * @since 2026-10-05 19:21:42
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SensitiveExampleTest {

    /**
     * 实际应用的请求入口。
     */
    private final MockMvc mvc;

    /**
     * 注入 MVC 客户端。
     *
     * @param mvc 请求入口
     */
    SensitiveExampleTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 根包接口保留替换和拒绝行为。
     *
     * @throws Exception 请求失败
     */
    @Test
    void processesAndRejectsThroughStarter() throws Exception {
        mvc.perform(post("/api/sensitive/process").contentType("text/plain").content("badge BAD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("badge ***"));
        mvc.perform(post("/api/sensitive/reject").contentType("text/plain").content("bad"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 旧结果协议和字段注解通过真实自动配置生效。
     *
     * @throws Exception 请求失败
     */
    @Test
    void exposesLegacyResultsAndAnnotationStrategies() throws Exception {
        mvc.perform(get("/api/sensitive/legacy").param("text", "😀 BADly"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.matches[0].word").value("bad"))
                .andExpect(jsonPath("$.data.matches[0].startIndex").value(3))
                .andExpect(jsonPath("$.data.matches[0].endIndex").value(5));
        mvc.perform(post("/api/sensitive/annotations/parameter").param("text", "bad").param("other", "bad"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("***:bad"));
        mvc.perform(post("/api/sensitive/annotations/document").contentType("application/json")
                        .content("{\"content\":\"badly\",\"other\":\"bad\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("*****"))
                .andExpect(jsonPath("$.data.other").value("bad"));
        mvc.perform(post("/api/sensitive/annotations/reject").contentType("text/plain").content("bad"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Web 过滤器真实注册，JSON 不被字符串替换破坏，排除路径仍保持原文。
     *
     * @throws Exception 请求失败
     */
    @Test
    void registersWebFilterAndPreservesJsonStructure() throws Exception {
        mvc.perform(post("/api/sensitive/web/json").contentType("application/json")
                        .content("{\"content\":\"bad\",\"items\":[\"badge\",\"bad\"],\"number\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("***"))
                .andExpect(jsonPath("$.data.items[0]").value("badge"))
                .andExpect(jsonPath("$.data.items[1]").value("***"))
                .andExpect(jsonPath("$.data.number").value(1));
        mvc.perform(get("/api/sensitive/web/query").param("selected", "bad").param("other", "bad"))
                .andExpect(jsonPath("$.data.selected").value("***"))
                .andExpect(jsonPath("$.data.other").value("bad"));
        mvc.perform(post("/api/sensitive/web/excluded").contentType("text/plain").content("bad"))
                .andExpect(jsonPath("$.data.text").value("bad"));
        mvc.perform(post("/api/sensitive/web/text").contentType("text/plain").content("x".repeat(4097)))
                .andExpect(status().isPayloadTooLarge());
    }
}
