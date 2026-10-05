package io.github.bytex0.desensitize.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 脱敏接口(MaskControllerTest)出站JSON集成验证
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MaskControllerTest {

    /**
     * MVC测试入口
     */
    private final MockMvc mvc;

    MaskControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void shouldMaskAndUseManagedCustomHandler() throws Exception {
        mvc.perform(get("/api/desensitize/profile")).andExpect(jsonPath("$.data.phone").value("138****8000"))
                .andExpect(jsonPath("$.data.name").value("张*")).andExpect(jsonPath("$.data.range").value("A####"))
                .andExpect(jsonPath("$.data.custom").value("managed")).andExpect(jsonPath("$.data.ordinary").value("public"));
    }

    @Test
    void shouldHandleNestedCollections() throws Exception {
        mvc.perform(get("/api/desensitize/list")).andExpect(jsonPath("$.data[1].email").value("a****@example.com"));
    }
}
