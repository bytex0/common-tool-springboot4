package io.github.bytex0.sensitive.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
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

    private final MockMvc mvc;

    SensitiveExampleTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void processesAndRejectsThroughStarter() throws Exception {
        mvc.perform(post("/api/sensitive/process").contentType("text/plain").content("badge BAD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("badge ***"));
        mvc.perform(post("/api/sensitive/reject").contentType("text/plain").content("bad"))
                .andExpect(status().isBadRequest());
    }
}
