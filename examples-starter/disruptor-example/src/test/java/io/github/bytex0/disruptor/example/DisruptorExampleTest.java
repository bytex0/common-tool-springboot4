package io.github.bytex0.disruptor.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    /**
     * 实际 Spring MVC 请求入口。
     */
    private final MockMvc mvc;

    /**
     * 注入请求入口。
     *
     * @param mvc 客户端
     */
    DisruptorExampleTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 类型化消费者确认和业务失败映射保持可用。
     *
     * @throws Exception 请求失败
     */
    @Test
    void confirmsAndReportsConsumerFailure() throws Exception {
        mvc.perform(post("/api/disruptor/send").param("value", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(post("/api/disruptor/send").param("value", "-1")).andExpect(status().isConflict());
        mvc.perform(post("/api/disruptor/send").param("value", "1").param("queue", "missing"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 两种注解线程模式确实执行，失败能够回传而非仅写日志。
     *
     * @throws Exception 请求失败
     */
    @Test
    void invokesAnnotatedConsumersWithConfiguredThreadModes() throws Exception {
        mvc.perform(post("/api/disruptor/listener").param("value", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.virtualThreads[0]").value(false));
        mvc.perform(post("/api/disruptor/listener").param("queue", "virtual").param("value", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.virtualMode").value(true));
        mvc.perform(post("/api/disruptor/listener").param("value", "-1")).andExpect(status().isConflict());
    }

    /**
     * 原动态建队列接口集成真实指标，删除后移除对应 Gauge。
     *
     * @throws Exception 请求失败
     */
    @Test
    void createsDynamicQueueAndCleansItsMetric() throws Exception {
        String name = "dynamic-" + UUID.randomUUID();
        try {
            mvc.perform(post("/api/disruptor/queues/" + name).param("size", "4").param("producer", "SINGLE"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.capacity").value(4))
                    .andExpect(jsonPath("$.data.metricPresent").value(true));
            mvc.perform(post("/api/disruptor/queues/" + name + "/send").param("value", "3")).andExpect(status().isOk());
            mvc.perform(post("/api/disruptor/queues/" + name)).andExpect(status().isBadRequest());
        } finally {
            mvc.perform(delete("/api/disruptor/queues/" + name)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/disruptor/metrics").param("name", name))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.present").value(false));
    }
}
