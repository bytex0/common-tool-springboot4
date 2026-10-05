package io.github.bytex0.dict.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 字典接口(DictControllerTest)实际JSON补充字段验证
 *
 * @author linshiqiang
 * @since 2026-10-05 16:08:16
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DictControllerTest {

    /**
     * MVC测试入口
     */
    private final MockMvc mvc;

    DictControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void shouldTranslateStringAndNumberWithoutReplacingCode() throws Exception {
        mvc.perform(get("/api/dict/sample")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("1")).andExpect(jsonPath("$.data.stateText").value("启用"))
                .andExpect(jsonPath("$.data.numeric").value(1)).andExpect(jsonPath("$.data.numericText").value("启用"));
    }

    @Test
    void shouldPreserveUnknownCode() throws Exception {
        mvc.perform(get("/api/dict/sample").param("status", "unknown"))
                .andExpect(jsonPath("$.data.state").value("unknown")).andExpect(jsonPath("$.data.stateText").doesNotExist());
    }
}
