package io.github.bytex0.common.model;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用响应(ApiResponseTest)工厂方法与 Jackson 3 兼容性测试
 *
 * @author bytex0
 * @since 2026-10-05 14:29:12
 */
class ApiResponseTest {

    /**
     * Spring Boot 4 默认使用的 Jackson 3 映射器
     */
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 验证空成功响应的默认字段。
     */
    @Test
    void shouldCreateEmptySuccess() {
        ApiResponse<Void> response = ApiResponse.ok();
        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getTs()).isPositive();
        assertThat(response.getData()).isNull();
        assertThat(response.getRequestId()).isNull();
    }

    /**
     * 验证数据响应自动生成请求标识。
     */
    @Test
    void shouldGenerateRequestIdForDataResponse() {
        ApiResponse<Map<String, String>> response = ApiResponse.ok(Map.of("name", "demo"));
        assertThat(response.getData()).containsEntry("name", "demo");
        assertThat(response.getRequestId()).matches("[0-9a-f]{32}");
        assertThat(response.isSuccess()).isTrue();
    }

    /**
     * 验证单个字符串仍表示请求标识。
     */
    @Test
    void shouldPreserveSingleStringAsRequestId() {
        ApiResponse<String> response = ApiResponse.ok("request-1");
        assertThat(response.getRequestId()).isEqualTo("request-1");
        assertThat(response.getData()).isNull();
        assertThat(ApiResponse.ok("request-1", "payload").getData()).isEqualTo("payload");
    }

    /**
     * 验证全部成功工厂重载。
     */
    @Test
    void shouldPreserveSuccessOverloads() {
        assertThat(ApiResponse.okOfMessage("done").getMessage()).isEqualTo("done");
        assertThat(ApiResponse.ok("request-1", "done", 42).getMessage()).isEqualTo("done");
        ApiResponse<Integer> response = ApiResponse.ok("request-1", 123L, 42);
        assertThat(response.getRequestId()).isEqualTo("request-1");
        assertThat(response.getTs()).isEqualTo(123L);
        assertThat(response.getData()).isEqualTo(42);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(ApiResponse.ok("request-1", 123L, 42, "done").getMessage()).isEqualTo("done");
    }

    /**
     * 验证全部失败工厂重载。
     */
    @Test
    void shouldPreserveFailureOverloads() {
        assertThat(ApiResponse.fail().getCode()).isEqualTo(500);
        assertThat(ApiResponse.fail(403).getCode()).isEqualTo(403);
        assertThat(ApiResponse.failOfMessage("denied", 403).getMessage()).isEqualTo("denied");
        ApiResponse<Void> response = ApiResponse.fail("request-1", "denied");
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getRequestId()).isEqualTo("request-1");
        assertThat(response.getMessage()).isEqualTo("denied");
        assertThat(response.getTs()).isPositive();
        assertThat(response.getData()).isNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(ApiResponse.fail("request-1", 403).getMessage()).isEqualTo("fail");
        assertThat(ApiResponse.fail("request-1", "denied", 403).getCode()).isEqualTo(403);
        assertThat(ApiResponse.fail("request-1", 123L, 403).getTs()).isEqualTo(123L);
        assertThat(ApiResponse.fail("request-1", 123L, 403, "denied").getMessage()).isEqualTo("denied");
    }

    /**
     * 验证空响应的成功判断结果。
     */
    @Test
    void shouldHandleNullInSuccessCheck() {
        assertThat(ApiResponse.isSuccess(null)).isFalse();
        assertThat(ApiResponse.isSuccess(ApiResponse.fail())).isFalse();
        assertThat(ApiResponse.isSuccess(ApiResponse.ok())).isTrue();
    }

    /**
     * 验证 Jackson 3 保留原字段协议。
     */
    @Test
    void shouldPreserveJsonFieldNamesWithJackson3() {
        ApiResponse<Map<String, String>> response = ApiResponse.ok("request-1", 123L, Map.of("name", "demo"));
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));
        assertThat(json.path("request_id").asString()).isEqualTo("request-1");
        assertThat(json.path("ts").asLong()).isEqualTo(123L);
        assertThat(json.path("code").asInt()).isZero();
        assertThat(json.path("message").asString()).isEqualTo("success");
        assertThat(json.path("data").path("name").asString()).isEqualTo("demo");
        assertThat(json.has("requestId")).isFalse();
    }

    /**
     * 验证空值字段不写入 JSON。
     */
    @Test
    void shouldOmitNullJsonFields() {
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(ApiResponse.ok()));
        assertThat(json.has("request_id")).isFalse();
        assertThat(json.has("data")).isFalse();
    }

    /**
     * 验证泛型响应反序列化。
     */
    @Test
    void shouldDeserializeGenericDataWithJackson3() {
        String json = """
                {"code":0,"message":"success","request_id":"request-1","ts":123,"data":["demo"]}
                """;
        ApiResponse<List<String>> response = jsonMapper.readValue(json, new TypeReference<>() { });
        assertThat(response.getRequestId()).isEqualTo("request-1");
        assertThat(response.getData()).containsExactly("demo");
    }
}
