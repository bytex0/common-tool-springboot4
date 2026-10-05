package io.github.bytex0.i18n.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    /**
     * 注入 MVC 测试入口。
     *
     * @param mvc 请求执行器
     */
    I18nControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 默认语言和请求语言都能得到实际格式化结果。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void shouldUseDefaultAndRequestedLocale() throws Exception {
        mvc.perform(get("/api/i18n/message").param("name", "Lin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("你好，Lin"));
        mvc.perform(get("/api/i18n/message").param("name", "Lin").locale(Locale.US))
                .andExpect(jsonPath("$.data.message").value("Hello, Lin"));
    }

    /**
     * 无效标签返回参数错误，未知编码按配置回退。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void shouldRejectInvalidLocaleAndFallbackToCode() throws Exception {
        mvc.perform(put("/api/i18n/message").param("language", "en;bad").param("code", "x").param("text", "y"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/i18n/message").param("code", "missing")).andExpect(jsonPath("$.data.message").value("missing"));
    }

    /**
     * 验证原默认文本重载和显式清空单个语言，不影响其他测试消息。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void shouldUseOriginalDefaultsAndClearExplicitly() throws Exception {
        mvc.perform(get("/api/i18n/default").param("code", "missing").param("language", "en_US")
                        .param("fallback", "Fallback {0}").param("name", "Lin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("Fallback Lin"));
        mvc.perform(put("/api/i18n/message").param("language", "fr").param("code", "temporary")
                        .param("text", "Bonjour")).andExpect(status().isOk());
        mvc.perform(delete("/api/i18n/messages").param("language", "fr")).andExpect(status().isOk());
        mvc.perform(get("/api/i18n/default").param("code", "temporary").param("language", "fr")
                        .param("fallback", "removed")).andExpect(jsonPath("$.data.message").value("removed"));
    }
}
