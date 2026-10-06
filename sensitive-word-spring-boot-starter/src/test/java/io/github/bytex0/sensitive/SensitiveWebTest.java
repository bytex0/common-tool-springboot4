package io.github.bytex0.sensitive;

import io.github.bytex0.sensitive.core.DfaSensitiveWordFilter;
import io.github.bytex0.sensitive.core.HandleType;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.core.SensitiveWordOptions;
import io.github.bytex0.sensitive.web.SensitiveWordWebFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web边界(SensitiveWebTest)验证结构化改写、范围选择和资源上限，不依赖运行中的服务器。
 *
 * @author linshiqiang
 * @since 2026-10-06 09:10:08
 */
class SensitiveWebTest {

    /**
     * JSON 保留键和数值，查询参数只处理指定名称，长度头与新正文一致。
     *
     * @throws Exception 过滤失败
     */
    @Test
    void preservesJsonStructureAndSelectedParameters() throws Exception {
        SensitiveWordOptions options = options();
        SensitiveWordWebFilter filter = filter(options);
        MockHttpServletRequest request = request("/protected/json", "application/json",
                "{\"text\":\"BAD\",\"number\":1,\"amount\":123456789.123456789,\"items\":[\"badge\",\"bad\"]}");
        request.addParameter("selected", "bad");
        request.addParameter("other", "bad");
        filter.doFilter(request, new MockHttpServletResponse(), (input, output) -> {
            HttpServletRequest current = (HttpServletRequest) input;
            assertThat(current.getParameter("selected")).isEqualTo("***");
            assertThat(current.getParameter("other")).isEqualTo("bad");
            byte[] body = current.getInputStream().readAllBytes();
            JsonNode json = JsonMapper.builder().build().readTree(body);
            assertThat(json.path("text").asString()).isEqualTo("***");
            assertThat(json.path("number").asInt()).isEqualTo(1);
            assertThat(json.path("items").get(0).asString()).isEqualTo("badge");
            assertThat(json.path("items").get(1).asString()).isEqualTo("***");
            assertThat(new String(body, StandardCharsets.UTF_8)).contains("123456789.123456789");
            assertThat(current.getContentLength()).isEqualTo(body.length);
            assertThat(current.getIntHeader("Content-Length")).isEqualTo(body.length);
        });
    }

    /**
     * 未命中的正文保留原始字节，排除路径不解析请求。
     *
     * @throws Exception 过滤失败
     */
    @Test
    void preservesUntouchedBodiesAndExcludedPaths() throws Exception {
        SensitiveWordWebFilter filter = filter(options());
        String original = "{ \"number\" : 1, \"text\" : \"badge\" }";
        filter.doFilter(request("/protected/json", "application/json", original), new MockHttpServletResponse(),
                (input, output) -> assertThat(new String(input.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(original));
        filter.doFilter(request("/protected/excluded", "application/json", "bad not json"), new MockHttpServletResponse(),
                (input, output) -> assertThat(new String(input.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("bad not json"));
    }

    /**
     * 超限、非法 JSON 和拒绝模式均不能进入业务，错误不包含原文。
     *
     * @throws Exception 过滤失败
     */
    @Test
    void rejectsOversizedInvalidAndProhibitedContent() throws Exception {
        SensitiveWordOptions options = options();
        options.getWeb().setMaxBodyBytes(4);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter(options).doFilter(request("/protected/text", "text/plain", "longer"), response,
                (input, output) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(called).isFalse();
        options.getWeb().setMaxBodyBytes(1024);
        response = new MockHttpServletResponse();
        filter(options).doFilter(request("/protected/json", "application/json", "{bad}"), response,
                (input, output) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(400);
        options.getWeb().setHandleType(HandleType.EXCEPTION);
        response = new MockHttpServletResponse();
        filter(options).doFilter(request("/protected/text", "text/plain", "bad"), response,
                (input, output) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).doesNotContain("bad");
        assertThat(called).isFalse();
    }

    /**
     * 请求体开关不影响参数选择，二进制正文不当作文本解析。
     *
     * @throws Exception 过滤失败
     */
    @Test
    void respectsBodySwitchAndBinaryContent() throws Exception {
        SensitiveWordOptions options = options();
        options.getWeb().setCheckBody(false);
        filter(options).doFilter(request("/protected/text", "text/plain", "bad"), new MockHttpServletResponse(),
                (input, output) -> assertThat(new String(input.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("bad"));
        options.getWeb().setCheckBody(true);
        filter(options).doFilter(request("/protected/binary", "application/octet-stream", "bad"), new MockHttpServletResponse(),
                (input, output) -> assertThat(new String(input.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("bad"));
    }

    /**
     * 构造测试选项。
     *
     * @return 配置
     */
    private SensitiveWordOptions options() {
        SensitiveWordOptions options = new SensitiveWordOptions();
        options.setWords(Set.of("bad"));
        options.setWhiteList(Set.of("badge"));
        options.getWeb().setHandleType(HandleType.REPLACE);
        options.getWeb().setUrlPatterns(List.of("/protected/**"));
        options.getWeb().setExcludePatterns(List.of("/protected/excluded"));
        options.getWeb().setCheckParams(Set.of("selected"));
        return options;
    }

    /**
     * 构造实际过滤器与词库组件。
     *
     * @param options 配置
     * @return 过滤器
     */
    private SensitiveWordWebFilter filter(SensitiveWordOptions options) {
        SensitiveWordOperations operations = new SensitiveWordOperations(new DfaSensitiveWordFilter(options), options);
        operations.afterPropertiesSet();
        return new SensitiveWordWebFilter(operations, options);
    }

    /**
     * 构造 UTF-8 请求。
     *
     * @param path 路径
     * @param contentType 媒体类型
     * @param body 请求体
     * @return 请求
     */
    private MockHttpServletRequest request(String path, String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
