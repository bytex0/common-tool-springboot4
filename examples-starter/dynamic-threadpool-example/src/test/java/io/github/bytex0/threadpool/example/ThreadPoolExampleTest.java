package io.github.bytex0.threadpool.example;

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
 * 真实线程池的 MVC 调整与任务执行验证。
 *
 * @author bytex0
 * @since 2026-10-05 20:05:22
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ThreadPoolExampleTest {

    private final MockMvc mvc;

    ThreadPoolExampleTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void resizesAndExecutes() throws Exception {
        mvc.perform(post("/api/pools/resize").param("core", "4").param("max", "4")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.core").value(4));
        mvc.perform(get("/api/pools/run")).andExpect(status().isOk());
        mvc.perform(post("/api/pools/resize").param("core", "4").param("max", "1")).andExpect(status().isBadRequest());
    }
}
