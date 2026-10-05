package io.github.bytex0.ratelimiter.example;

import io.github.bytex0.ratelimiter.exception.RateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 限流接口(RateControllerTest)拒绝响应测试
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@WebMvcTest(RateController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RateControllerTest {

    /**
     * 外部服务边界
     */
    @MockitoBean
    private RateService service;

    /**
     * MVC入口
     */
    private final MockMvc mvc;

    RateControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void shouldReturnTooManyRequests() throws Exception {
        doThrow(new RateLimitException()).when(service).annotated("key");
        mvc.perform(get("/api/rate/annotated").param("key", "key"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(429));
    }
}
