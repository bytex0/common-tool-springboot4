package io.github.bytex0.sftp.example;

import io.github.bytex0.sftp.SftpTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 普通构建隔离远端 SFTP 的接口测试。
 *
 * @author bytex0
 * @since 2026-10-05 19:47:49
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SftpExampleTest {

    @MockitoBean
    SftpTemplate template;

    private final MockMvc mvc;

    SftpExampleTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void delegatesToTemplateAndValidatesFilename() throws Exception {
        when(template.list("upload")).thenReturn(List.of("test.bin"));
        mvc.perform(get("/api/sftp/files")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.names[0]").value("test.bin"));
        mvc.perform(get("/api/sftp/file/wrong.txt")).andExpect(status().isBadRequest());
    }
}
