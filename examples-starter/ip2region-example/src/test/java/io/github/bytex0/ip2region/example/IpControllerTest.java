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

    private final MockMvc mvc;

    IpControllerTest(@Qualifier("mockMvc") MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void searchesDatabaseAndRejectsHostNames() throws Exception {
        mvc.perform(get("/api/ip/search").param("ip", "8.8.8.8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.country").value("美国"));
        mvc.perform(get("/api/ip/search").param("ip", "localhost")).andExpect(status().isBadRequest());
    }
}
