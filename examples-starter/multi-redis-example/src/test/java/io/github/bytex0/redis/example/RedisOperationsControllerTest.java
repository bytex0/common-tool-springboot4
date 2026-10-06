package io.github.bytex0.redis.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis 操作接口测试(RedisOperationsControllerTest)验证响应数据和参数错误，外部连接以替身隔离。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:07:28
 */
@WebMvcTest(RedisOperationsController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RedisOperationsControllerTest {

    /**
     * 外部场景边界。
     */
    @MockitoBean
    private RedisScenarioService scenarios;

    /**
     * MVC 请求入口。
     */
    private final MockMvc mvc;

    /**
     * 注入 MVC 客户端。
     *
     * @param mvc 请求客户端
     */
    RedisOperationsControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 分组操作结果以数据形式返回，不暴露 SDK 内部对象。
     *
     * @throws Exception 请求或替身配置失败时抛出
     */
    @Test
    void shouldReturnScenarioResult() throws Exception {
        when(scenarios.run("hash", "main", "run")).thenReturn(Map.of("count", 3));
        mvc.perform(post("/api/redis/structures/hash").param("run", "run"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(3));
    }

    /**
     * 非法运行标识返回无敏感上下文的 400。
     *
     * @throws Exception 请求或替身配置失败时抛出
     */
    @Test
    void shouldRejectInvalidRunWithoutLeakingMessage() throws Exception {
        when(scenarios.run("hash", "main", "invalid")).thenThrow(new IllegalArgumentException("internal-detail"));
        mvc.perform(post("/api/redis/structures/hash").param("run", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Redis场景参数不合法"));
    }
}
