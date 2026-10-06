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
 * Excel 原接口测试(LegacyExcelControllerTest)在真实 Starter 和独立 H2 中验证事务及资源清理。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:23:54
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LegacyExcelControllerTest {

    /**
     * 实际 MVC 请求客户端。
     */
    private final MockMvc mvc;

    /**
     * 注入测试客户端。
     *
     * @param mvc MVC 客户端
     */
    LegacyExcelControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * Starter 的事务开关决定失败批次是否回滚，成功/失败统计保持不变。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldApplyBatchTransactionsAndReportProgress() throws Exception {
        byte[] input = workbook(5);
        mvc.perform(multipart("/api/excel/legacy/import").file(file(input))
                        .param("continueOnError", "true").param("failAt", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.success").value(3)).andExpect(jsonPath("$.data.failed").value(2))
                .andExpect(jsonPath("$.data.stored.length()").value(3))
                .andExpect(jsonPath("$.data.stored[2].id").value(5))
                .andExpect(jsonPath("$.data.stored[2].name").value("名称5"))
                .andExpect(jsonPath("$.data.progress[-1].total").value(5));
        mvc.perform(multipart("/api/excel/legacy/import").file(file(input))
                        .param("continueOnError", "true").param("failAt", "3").param("transactional", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stored.length()").value(4))
                .andExpect(jsonPath("$.data.stored[2].id").value(3));
        assertClean();
    }

    /**
     * 多 Sheet 原处理器导出后可用原导入上下文选择第三个 Sheet。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldReadSelectedSheetThroughOriginalImportContext() throws Exception {
        byte[] workbook = mvc.perform(get("/api/excel/legacy/export").param("count", "11"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(multipart("/api/excel/legacy/import").file(file(workbook))
                        .param("mode", "simple").param("sheet", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.stored[0].id").value(11));
        assertClean();
    }

    /**
     * 业务失败与参数错误返回 400，失败分支也清理本次数据库数据和临时文件。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    @Test
    void shouldCleanAfterBusinessFailureAndRejectBadMode() throws Exception {
        mvc.perform(multipart("/api/excel/legacy/import").file(file(workbook(5))).param("failAt", "3"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/excel/legacy/export").param("mode", "unknown"))
                .andExpect(status().isBadRequest());
        assertClean();
    }

    /**
     * 从实际响应生成本次导入文件。
     *
     * @param count 行数
     * @return 工作簿内容
     * @throws Exception MVC 请求失败时抛出
     */
    private byte[] workbook(int count) throws Exception {
        return mvc.perform(get("/api/excel/legacy/export").param("mode", "simple")
                        .param("count", Integer.toString(count)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /**
     * 包装上传数据。
     *
     * @param data 内容
     * @return 上传文件
     */
    private MockMultipartFile file(byte[] data) {
        return new MockMultipartFile("file", "input.xlsx", "application/octet-stream", data);
    }

    /**
     * 核对实际持久化及文件资源已清理。
     *
     * @throws Exception MVC 请求失败时抛出
     */
    private void assertClean() throws Exception {
        mvc.perform(get("/api/excel/legacy/state")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").value(0)).andExpect(jsonPath("$.data.temporaryFiles").value(0));
    }
}
