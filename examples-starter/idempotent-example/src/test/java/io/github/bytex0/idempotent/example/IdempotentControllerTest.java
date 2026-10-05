package io.github.bytex0.idempotent.example;

import io.github.bytex0.idempotent.exception.IdempotentException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
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

    IdempotentControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void shouldReturnConflictForDuplicate() throws Exception {
        when(service.run("key", 50, false)).thenThrow(new IdempotentException("duplicate"));
        mvc.perform(post("/api/idempotent/run").param("key", "key"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }
}
