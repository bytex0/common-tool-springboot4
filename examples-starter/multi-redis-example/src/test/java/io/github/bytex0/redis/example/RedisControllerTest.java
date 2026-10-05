package io.github.bytex0.redis.example;

import io.github.bytex0.redis.MultiRedisManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis接口(RedisControllerTest)路由和错误响应测试
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
@WebMvcTest(RedisController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RedisControllerTest {

    /**
     * 外部服务边界，真实验证由Docker自动化完成
     */
    @MockitoBean
    private MultiRedisManager manager;

    /**
     * MVC测试入口
     */
    private final MockMvc mvc;

    RedisControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void shouldListConfiguredNames() throws Exception {
        when(manager.names()).thenReturn(Set.of("main"));
        mvc.perform(get("/api/redis/names")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("main"));
    }

    @Test
    void shouldRejectUnknownClient() throws Exception {
        when(manager.get("unknown")).thenThrow(new IllegalArgumentException("unknown"));
        mvc.perform(get("/api/redis/value").param("client", "unknown").param("key", "test"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
}
