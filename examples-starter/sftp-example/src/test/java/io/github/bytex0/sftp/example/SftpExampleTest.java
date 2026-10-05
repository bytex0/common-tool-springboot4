package io.github.bytex0.sftp.example;

import io.github.bytex0.sftp.SftpTemplate;
import io.github.bytex0.sftp.SftpProperties;
import io.github.bytex0.sftp.core.JschConnectionPool;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    /**
     * 普通构建替换外部 SFTP 操作。
     */
    @MockitoBean
    SftpTemplate template;

    /**
     * 命名池边界替身。
     */
    @MockitoBean
    JschConnectionPool pools;

    /**
     * 测试配置替身，不包含真实凭据。
     */
    @MockitoBean
    SftpProperties properties;

    /**
     * MVC 请求入口。
     */
    private final MockMvc mvc;

    /**
     * 注入请求客户端。
     *
     * @param mvc 请求入口
     */
    SftpExampleTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * 既有文件接口通过模板调用，非法文件名被拒绝。
     *
     * @throws Exception 请求失败
     */
    @Test
    void delegatesToTemplateAndValidatesFilename() throws Exception {
        when(template.list("upload")).thenReturn(List.of("test.bin"));
        mvc.perform(get("/api/sftp/files")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.names[0]").value("test.bin"));
        mvc.perform(get("/api/sftp/file/wrong.txt")).andExpect(status().isBadRequest());
    }

    /**
     * 命名池接口使用真实池管理器边界，并固定测试容量与等待时间。
     *
     * @throws Exception 请求失败
     */
    @Test
    void createsBoundedNamedPoolThroughStarter() throws Exception {
        doReturn(SftpProperties.builder()).when(properties).toBuilder();
        when(pools.names()).thenReturn(Set.of("default", "named"));
        mvc.perform(post("/api/sftp/pools/named")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.names").isArray());
        ArgumentCaptor<SftpProperties> configuration = ArgumentCaptor.forClass(SftpProperties.class);
        verify(pools).buildPool(eq("named"), configuration.capture());
        assertThat(configuration.getValue().getMaxTotal()).isEqualTo(1);
        assertThat(configuration.getValue().getMaxWait()).isEqualTo(Duration.ofMillis(100));
        mvc.perform(post("/api/sftp/pools/default")).andExpect(status().isBadRequest());
    }
}
