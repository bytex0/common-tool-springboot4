package io.github.bytex0.ip2region.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完整应用与真实 XDB 数据库的接口测试。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IpControllerTest {

    /**
     * 模拟 MVC 请求入口。
     */
    private final MockMvc mvc;

    /**
     * 注入测试入口。
     *
     * @param mvc MVC 请求执行器
     */
    IpControllerTest(@Qualifier("mockMvc") MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 验证真实记录及无 DNS 主机名查询边界。
     *
     * @throws Exception HTTP 模拟请求失败
     */
    @Test
    void searchesDatabaseAndRejectsHostNames() throws Exception {
        mvc.perform(get("/api/ip/search").param("ip", "8.8.8.8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.country").value("美国"));
        mvc.perform(get("/api/ip/search").param("ip", "localhost")).andExpect(status().isBadRequest());
    }
}
