package io.github.bytex0.disruptor.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 实际队列的 MVC 消费确认测试。
 *
 * @author bytex0
 * @since 2026-10-05 19:39:53
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DisruptorExampleTest {

    private final MockMvc mvc;

    DisruptorExampleTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void confirmsAndReportsConsumerFailure() throws Exception {
        mvc.perform(post("/api/disruptor/send").param("value", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(post("/api/disruptor/send").param("value", "-1")).andExpect(status().isConflict());
        mvc.perform(post("/api/disruptor/send").param("value", "1").param("queue", "missing"))
                .andExpect(status().isBadRequest());
    }
}
