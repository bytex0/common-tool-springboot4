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
 * @author bytex0
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

    /**
     * 注入真实应用的请求测试入口。
     *
     * @param mvc 请求入口
     */
    DictControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 注解保留字符串与数值编码并追加文本。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldTranslateStringAndNumberWithoutReplacingCode() throws Exception {
        mvc.perform(get("/api/dict/sample")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("1")).andExpect(jsonPath("$.data.stateText").value("启用"))
                .andExpect(jsonPath("$.data.numeric").value(1)).andExpect(jsonPath("$.data.numericText").value("启用"));
    }

    /**
     * 未命中字典不能改变原值或生成虚假文本。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldPreserveUnknownCode() throws Exception {
        mvc.perform(get("/api/dict/sample").param("status", "unknown"))
                .andExpect(jsonPath("$.data.state").value("unknown")).andExpect(jsonPath("$.data.stateText").doesNotExist());
    }

    /**
     * 用嵌入数据库验证表字段回退，注入形状的参数也只作为编码处理。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldTranslateFromDatabaseWithBoundParameters() throws Exception {
        mvc.perform(get("/api/dict/department").param("code", "D1"))
                .andExpect(jsonPath("$.data.code").value("D1"))
                .andExpect(jsonPath("$.data.codeText").value("Engineering"));
        mvc.perform(get("/api/dict/department").param("code", "x' OR '1'='1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.codeText").doesNotExist());
        mvc.perform(get("/api/dict/legacy").param("value", "Engineering"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("Engineering"));
    }
}
