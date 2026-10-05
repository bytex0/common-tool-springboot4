package io.github.bytex0.lock.example;

import io.github.bytex0.lock.enums.LockType;
import io.github.bytex0.lock.exception.LockException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 锁接口(LockControllerTest)状态响应测试
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@WebMvcTest(LockController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LockControllerTest {

    /**
     * 服务边界
     */
    @MockitoBean
    private LockService service;

    /**
     * MVC测试入口
     */
    private final MockMvc mvc;

    /**
     * 注入请求测试入口。
     *
     * @param mvc 测试入口
     */
    LockControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 锁异常转换为 423 业务响应。
     *
     * @throws Throwable 请求或模拟服务调用失败
     */
    @Test
    void shouldReturnLockedStatus() throws Throwable {
        when(service.run("key", LockType.REDISSON_LOCK, 1, 3000, 50, false)).thenThrow(new LockException("busy"));
        mvc.perform(get("/api/lock/run").param("key", "key")).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value(423));
    }

    /**
     * 原工厂和完整动态规则接口均调用服务边界。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldExposeFactoryAndDynamicRules() throws Exception {
        when(service.factory("key", LockType.REDISSON_LOCK)).thenReturn(1L);
        when(service.dynamic("key", 2)).thenReturn(2L);
        mvc.perform(get("/api/lock/factory").param("key", "key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(1));
        mvc.perform(get("/api/lock/dynamic").param("key", "key").param("permits", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(2));
    }

    /**
     * 动态规则参数错误映射为 400。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldRejectInvalidDynamicPermits() throws Exception {
        when(service.permits("key", 0)).thenThrow(new IllegalArgumentException("permits"));
        mvc.perform(get("/api/lock/permits").param("key", "key").param("permits", "0")).andExpect(status().isBadRequest());
    }
}
