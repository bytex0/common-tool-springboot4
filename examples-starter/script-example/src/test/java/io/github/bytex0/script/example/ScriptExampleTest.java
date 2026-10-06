package io.github.bytex0.script.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 Groovy 执行器验证示例接口。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ScriptExampleTest {

    /**
     * 集成真实自动配置的 MVC 客户端。
     */
    private final MockMvc mvc;

    /**
     * 注入测试客户端。
     *
     * @param mvc MVC 客户端
     */
    ScriptExampleTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 验证既有脚本体接口和未知脚本拒绝行为。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void runsOnlyKnownScripts() throws Exception {
        mvc.perform(get("/api/script/run").param("a", "2").param("b", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(5));
        mvc.perform(get("/api/script/run").param("name", "untrusted")).andExpect(status().isBadRequest());
    }

    /**
     * 验证五种语言均通过 Starter 自动装配执行，Lua 保留字符串协议。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void runsAllTypedLanguages() throws Exception {
        for (String type : new String[]{"GROOVY", "JAVASCRIPT", "PYTHON", "JAVA"}) {
            mvc.perform(get("/api/script/typed/run").param("type", type).param("a", "2").param("b", "3"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(5));
        }
        mvc.perform(get("/api/script/typed/run").param("type", "LUA").param("a", "2").param("b", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("5"));
        mvc.perform(get("/api/script/typed/run").param("type", "UNKNOWN")).andExpect(status().isBadRequest());
    }

    /**
     * 验证缓存管理和校验失败的真实服务路径。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void managesCacheAndValidatesSource() throws Exception {
        mvc.perform(get("/api/script/typed/cache")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.first").value(1))
                .andExpect(jsonPath("$.data.changed").value(2))
                .andExpect(jsonPath("$.data.refreshed").value(3))
                .andExpect(jsonPath("$.data.afterRemoval").value(4));
        mvc.perform(get("/api/script/typed/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
        mvc.perform(get("/api/script/typed/validate").param("valid", "false")).andExpect(status().isBadRequest());
    }
}
