package io.github.bytex0.i18n.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 国际化接口(I18nControllerTest)消息查询及参数测试
 *
 * @author bytex0
 * @since 2026-10-05 15:49:14
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class I18nControllerTest {

    /**
     * MVC测试入口
     */
    private final MockMvc mvc;

    I18nControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void shouldUseDefaultAndRequestedLocale() throws Exception {
        mvc.perform(get("/api/i18n/message").param("name", "Lin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("你好，Lin"));
        mvc.perform(get("/api/i18n/message").param("name", "Lin").locale(Locale.US))
                .andExpect(jsonPath("$.data.message").value("Hello, Lin"));
    }

    @Test
    void shouldRejectInvalidLocaleAndFallbackToCode() throws Exception {
        mvc.perform(put("/api/i18n/message").param("language", "en;bad").param("code", "x").param("text", "y"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/i18n/message").param("code", "missing")).andExpect(jsonPath("$.data.message").value("missing"));
    }
}
