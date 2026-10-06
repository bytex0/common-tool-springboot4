package io.github.bytex0.desensitize.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 脱敏接口(MaskControllerTest)出站JSON集成验证
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MaskControllerTest {

    /**
     * MVC测试入口
     */
    private final MockMvc mvc;

    /**
     * 注入实际应用上下文中的 MVC 客户端。
     *
     * @param mvc 请求客户端
     */
    MaskControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 出站 JSON 使用内置策略、显式范围和托管自定义 Bean。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldMaskAndUseManagedCustomHandler() throws Exception {
        mvc.perform(get("/api/desensitize/profile")).andExpect(jsonPath("$.data.phone").value("138****8000"))
                .andExpect(jsonPath("$.data.name").value("张*")).andExpect(jsonPath("$.data.range").value("A####"))
                .andExpect(jsonPath("$.data.custom").value("managed")).andExpect(jsonPath("$.data.ordinary").value("public"));
    }

    /**
     * 嵌套集合的规则与单对象一致。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldHandleNestedCollections() throws Exception {
        mvc.perform(get("/api/desensitize/list")).andExpect(jsonPath("$.data[1].email").value("a****@example.com"));
    }

    /**
     * 三个引擎均可处理继承模型与 record，失败响应不包含原文字段。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldExerciseAllSerializationEnginesAndFailClosed() throws Exception {
        for (String engine : new String[]{"jackson", "fastjson", "fastjson2"}) {
            mvc.perform(get("/api/desensitize/engines/" + engine)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phone").value("138****8000"))
                    .andExpect(jsonPath("$.data.full_name").value("张*丰"));
            mvc.perform(get("/api/desensitize/records/" + engine)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phone").value("138****8000"));
            mvc.perform(get("/api/desensitize/failure/" + engine)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.secret").doesNotExist());
        }
    }

    /**
     * String 结果必须放入 data，不能误用把字符串当作请求 ID 的响应重载。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldReturnUnicodeRangeAsResponseData() throws Exception {
        mvc.perform(get("/api/desensitize/range").param("value", "\uD801\uDC00AB")
                        .param("start", "1").param("token", "#"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("\uD801\uDC00##"));
        mvc.perform(get("/api/desensitize/range").param("value", "ABC").param("start", "2").param("end", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/desensitize/range").param("value", "ABC").param("start", "0").param("token", ""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/desensitize/range").param("value", "ABC").param("start", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("A**"));
    }
}
