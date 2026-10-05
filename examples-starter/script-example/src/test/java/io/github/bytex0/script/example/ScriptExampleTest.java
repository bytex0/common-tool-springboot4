package io.github.bytex0.script.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 Groovy 执行器验证示例接口。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ScriptExampleTest {

    private final MockMvc mvc;

    ScriptExampleTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void runsOnlyKnownScripts() throws Exception {
        mvc.perform(get("/api/script/run").param("a", "2").param("b", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(5));
        mvc.perform(get("/api/script/run").param("name", "untrusted")).andExpect(status().isBadRequest());
    }
}
