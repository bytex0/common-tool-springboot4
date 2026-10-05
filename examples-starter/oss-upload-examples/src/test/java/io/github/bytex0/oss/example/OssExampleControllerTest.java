package io.github.bytex0.oss.example;

import io.github.bytex0.oss.OssProperties;
import io.github.bytex0.oss.client.OssClient;
import io.github.bytex0.oss.example.controller.OssExampleController;
import io.github.bytex0.oss.model.ChunkMergeDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对象存储接口(OssExampleControllerTest)参数绑定、JSON 和错误响应测试
 *
 * @author linshiqiang
 * @since 2026-10-05 14:57:26
 */
@WebMvcTest(controllers = OssExampleController.class, properties = "oss.bucket-name=test-bucket")
@Import(OssExampleControllerTest.PropertiesConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OssExampleControllerTest {

    /**
     * 仅替换外部存储，真实联调由自动化脚本另行执行
     */
    @MockitoBean
    private OssClient ossClient;

    /**
     * Web MVC 测试客户端
     */
    private final MockMvc mvc;

    OssExampleControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void shouldUploadUsingConfiguredStarterAndBucket() throws Exception {
        when(ossClient.putObject(eq("test-bucket"), eq("file.txt"), any(InputStream.class), eq("text/plain")))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        mvc.perform(multipart("/api/oss/objects")
                        .file(new MockMultipartFile("file", "file.txt", "text/plain", "hello".getBytes()))
                        .param("objectName", "file.txt"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eTag").value("etag")).andExpect(jsonPath("$.data.size").value(5));
        verify(ossClient).putObject(eq("test-bucket"), eq("file.txt"), any(InputStream.class), eq("text/plain"));
    }

    @Test
    void shouldBindETagAndTranslateSdkResponse() throws Exception {
        when(ossClient.completeMultipartUpload(any(ChunkMergeDTO.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag("merged").build());
        mvc.perform(post("/api/oss/multipart/complete").contentType(MediaType.APPLICATION_JSON).content("""
                        {"objectName":"file","uploadId":"upload","parts":[{"partNumber":1,"eTag":"part-tag"}]}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eTag").value("merged"));
        ArgumentCaptor<ChunkMergeDTO> request = ArgumentCaptor.forClass(ChunkMergeDTO.class);
        verify(ossClient).completeMultipartUpload(request.capture());
        assertThat(request.getValue().getBucketName()).isEqualTo("test-bucket");
        assertThat(request.getValue().getChunkList().getFirst().eTag()).isEqualTo("part-tag");
    }

    @Test
    void shouldRejectInvalidNestedPartsWithoutCallingStarter() throws Exception {
        mvc.perform(post("/api/oss/multipart/complete").contentType(MediaType.APPLICATION_JSON).content("""
                        {"objectName":"file","uploadId":"upload","parts":[{"partNumber":0,"eTag":""}]}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(ossClient);
    }

    @Test
    void shouldRejectUnknownUploadMode() throws Exception {
        mvc.perform(multipart("/api/oss/objects")
                        .file(new MockMultipartFile("file", new byte[0]))
                        .param("objectName", "file").param("mode", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(ossClient);
    }

    @Test
    void shouldHideSdkRequestDetailsInErrors() throws Exception {
        when(ossClient.getObjectMetadata("test-bucket", "missing"))
                .thenThrow(S3Exception.builder().statusCode(404).message("internal SDK request details").build());
        mvc.perform(get("/api/oss/metadata").param("objectName", "missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("对象存储请求失败"));
    }

    @Test
    void shouldPassRecursiveSelectionToStarter() throws Exception {
        when(ossClient.getAllObjectsByPrefix("test-bucket", "dir/", false)).thenReturn(List.of());
        mvc.perform(get("/api/oss/objects").param("prefix", "dir/").param("recursive", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(ossClient).getAllObjectsByPrefix("test-bucket", "dir/", false);
    }

    /**
     * 对象存储示例(PropertiesConfiguration)隔离测试配置
     *
     * @author linshiqiang
     * @since 2026-10-05 14:57:26
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class PropertiesConfiguration {

        @Bean
        OssProperties ossProperties() {
            OssProperties properties = new OssProperties();
            properties.setBucketName("test-bucket");
            return properties;
        }
    }
}
