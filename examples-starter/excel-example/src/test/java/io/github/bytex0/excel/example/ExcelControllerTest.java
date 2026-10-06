package io.github.bytex0.excel.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Excel接口(ExcelControllerTest)实际响应文件与导入回读测试
 *
 * @author bytex0
 * @since 2026-10-05 15:36:55
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ExcelControllerTest {

    /**
     * MVC测试入口
     */
    private final MockMvc mvc;

    /**
     * 注入实际 MVC 客户端。
     *
     * @param mvc 测试入口
     */
    ExcelControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 响应工作簿可以再次上传并按顺序读取。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldRoundTripResponseWorkbook() throws Exception {
        byte[] data = mvc.perform(get("/api/excel/export").param("count", "4"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(multipart("/api/excel/import")
                        .file(new MockMultipartFile("file", "data.xlsx", "application/octet-stream", data)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.ids[3]").value(4));
    }

    /**
     * 非法行上限不能开始写入响应工作簿。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldRejectInvalidRowLimit() throws Exception {
        mvc.perform(get("/api/excel/export").param("rowsPerSheet", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/excel/export").param("rowsPerSheet", "1000001"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 文本数据不能被当作有效空 Excel。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldRejectMalformedWorkbook() throws Exception {
        mvc.perform(multipart("/api/excel/import")
                        .file(new MockMultipartFile("file", "not-excel".getBytes())))
                .andExpect(status().isBadRequest());
    }
}
