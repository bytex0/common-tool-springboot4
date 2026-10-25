package io.github.bytex0.script.example;

import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptCompileException;
import io.github.bytex0.script.service.ScriptService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 依赖示例接口的响应与失败协议测试；普通构建不启用外部仓库解析，真实解析由接口脚本验收。
 *
 * @author bytex0
 * @since 2026-10-25 11:10:02
 */
@SpringBootTest(properties = "script.example.ivy-enabled=true")
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DependencyScriptControllerTest {

    /**
     * 当前上下文服务替身，不在普通构建中读取用户 Grape 配置或下载依赖。
     */
    @MockitoBean
    private ScriptService service;

    /**
     * 测试 MVC 客户端。
     */
    private final MockMvc mvc;

    /**
     * 注入测试客户端。
     *
     * @param mvc MVC 客户端
     */
    DependencyScriptControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 验证依赖结果返回及编译失败不会暴露内部异常消息。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    void returnsDependencyPayloadAndSafeCompilationFailure() throws Exception {
        when(service.execute(eq("ivy-fixture-script-payload"), eq(ScriptType.GROOVY), anyString(), anyMap()))
                .thenReturn("fixture-result");
        when(service.execute(eq("ivy-fixture-script-missing"), eq(ScriptType.GROOVY), anyString(), anyMap()))
                .thenThrow(new ScriptCompileException("internal fixture path"));
        mvc.perform(get("/api/script/typed/dependency")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("fixture-result"));
        mvc.perform(get("/api/script/typed/dependency").param("available", "false"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").doesNotExist());
    }
}
