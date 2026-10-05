package io.github.bytex0.idempotent.example;

import io.github.bytex0.idempotent.exception.IdempotentException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 幂等接口(IdempotentControllerTest)重复请求响应测试
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@WebMvcTest(IdempotentController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IdempotentControllerTest {

    /**
     * 外部处理边界
     */
    @MockitoBean
    private IdempotentService service;

    /**
     * MVC入口
     */
    private final MockMvc mvc;

    /**
     * 注入 MVC 测试入口。
     *
     * @param mvc 当前切片请求入口
     */
    IdempotentControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 业务去重异常映射为 409。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void shouldReturnConflictForDuplicate() throws Exception {
        when(service.run("key", 50, false)).thenThrow(new IdempotentException("duplicate"));
        mvc.perform(post("/api/idempotent/run").param("key", "key"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }

    /**
     * 原独立检查由实际服务边界承接，成功返回可断言的 JSON。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void shouldReserveThroughService() throws Exception {
        mvc.perform(post("/api/idempotent/reserve").param("key", "original"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reserved").value(true));
        verify(service).reserve("original");
    }

    /**
     * 独立检查的重复和参数错误采用一致的 HTTP 协议。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void shouldTranslateReservationErrors() throws Exception {
        doThrow(new IdempotentException("duplicate")).when(service).reserve("duplicate");
        doThrow(new IllegalArgumentException("empty")).when(service).reserve("");
        mvc.perform(post("/api/idempotent/reserve").param("key", "duplicate")).andExpect(status().isConflict());
        mvc.perform(post("/api/idempotent/reserve").param("key", "")).andExpect(status().isBadRequest());
    }
}
