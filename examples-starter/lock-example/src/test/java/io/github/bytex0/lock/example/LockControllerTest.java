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

    LockControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void shouldReturnLockedStatus() throws Throwable {
        when(service.run("key", LockType.REDISSON_LOCK, 1, 3000, 50, false)).thenThrow(new LockException("busy"));
        mvc.perform(get("/api/lock/run").param("key", "key")).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value(423));
    }
}
