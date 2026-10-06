package io.github.bytex0.example;

import io.github.bytex0.core.ApplicationInfoInitialize;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.net.http.HttpRequest.BodyPublishers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基础示例(CommonToolExampleApplicationTest)自动装配与 HTTP 集成测试
 *
 * @author bytex0
 * @since 2026-10-05 14:29:12
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CommonToolExampleApplicationTest {

    /**
     * 包含随机 HTTP 端口的应用环境
     */
    private final Environment environment;

    /**
     * 由 Spring Boot 自动配置的 Jackson 3 映射器
     */
    private final JsonMapper jsonMapper;

    /**
     * 通过 Starter 自动装配而非组件扫描注册的启动监听器
     */
    private final ApplicationInfoInitialize applicationInfoInitialize;

    /**
     * 注入实际自动配置的应用组件。
     *
     * @param environment 应用环境
     * @param jsonMapper Jackson 3 映射器
     * @param applicationInfoInitialize 启动监听器
     */
    CommonToolExampleApplicationTest(Environment environment, JsonMapper jsonMapper,
                                     ApplicationInfoInitialize applicationInfoInitialize) {
        this.environment = environment;
        this.jsonMapper = jsonMapper;
        this.applicationInfoInitialize = applicationInfoInitialize;
    }

    /**
     * 验证真实 HTTP 响应和原 JSON 字段协议。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldDiscoverStarterAndServeDemoResponse() throws Exception {
        assertThat(applicationInfoInitialize).isNotNull();
        HttpResponse<String> response = get("/api/demo/ping");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = jsonMapper.readTree(response.body());
        assertThat(json.path("code").asInt()).isZero();
        assertThat(json.path("message").asString()).isEqualTo("success");
        assertThat(json.path("request_id").asString()).matches("[0-9a-f]{32}");
        assertThat(json.path("ts").asLong()).isPositive();
        assertThat(json.path("data").path("application").asString()).isEqualTo("common-tool-example");
        assertThat(json.path("data").path("status").asString()).isEqualTo("UP");
    }

    /**
     * 验证没有外部服务时应用和内存数据库健康。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldExposeHealthWithoutExternalInfrastructure() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(response.body()).path("status").asString()).isEqualTo("UP");
    }

    /**
     * 验证实际注入的 ID、轮询、分片和 MDC 工具。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldServeBasicCapabilities() throws Exception {
        JsonNode ids = jsonMapper.readTree(get("/api/common/ids?count=100").body()).path("data");
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < ids.size(); index++) {
            unique.add(ids.get(index).asString());
        }
        assertThat(unique).hasSize(100);
        assertThat(get("/api/common/ids?count=0").statusCode()).isEqualTo(400);
        JsonNode trace = jsonMapper.readTree(get("/api/common/trace").body()).path("data");
        assertThat(trace.path("before").asString()).isEqualTo("request|event");
        assertThat(trace.path("after").asString()).isEqualTo("request|event");
        JsonNode tools = jsonMapper.readTree(get("/api/common/tools").body()).path("data");
        assertThat(tools.path("selected").asString()).isEqualTo("only");
        assertThat(tools.path("shard").toString()).isEqualTo("[1,3]");
    }

    /**
     * 验证真实 JDBC 事务提交和回滚，不仅检查 HTTP 状态。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldInvokeCallbacksOnlyAfterRealCommit() throws Exception {
        HttpResponse<String> committed = post("/api/common/transaction", "");
        assertThat(committed.statusCode()).isEqualTo(200);
        JsonNode commit = jsonMapper.readTree(committed.body()).path("data");
        assertThat(commit.path("rows").asInt()).isEqualTo(1);
        assertThat(commit.path("callbacks").asInt()).isEqualTo(2);
        HttpResponse<String> rolledBack = post("/api/common/transaction?rollback=true", "");
        assertThat(rolledBack.statusCode()).isEqualTo(200);
        JsonNode rollback = jsonMapper.readTree(rolledBack.body()).path("data");
        assertThat(rollback.path("rows").asInt()).isZero();
        assertThat(rollback.path("callbacks").asInt()).isZero();
    }

    /**
     * 验证注入校验器与并行消费的正常和错误响应。
     *
     * @throws Exception 请求失败
     */
    @Test
    void shouldValidateAndHandleParallelFailure() throws Exception {
        assertThat(post("/api/common/validate", "{\"name\":\"test\",\"age\":1}").statusCode()).isEqualTo(200);
        assertThat(post("/api/common/validate", "{\"name\":\"\",\"age\":-1}").statusCode()).isEqualTo(400);
        HttpResponse<String> success = post("/api/common/parallel", "");
        assertThat(success.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(success.body()).path("data").asInt()).isEqualTo(10);
        assertThat(post("/api/common/parallel?fail=true", "").statusCode()).isEqualTo(400);
    }

    /**
     * 请求随机端口的真实 HTTP 接口，结束后关闭客户端。
     *
     * @param path 请求路径
     * @return HTTP 响应
     * @throws Exception 请求失败
     */
    private HttpResponse<String> get(String path) throws Exception {
        String port = environment.getRequiredProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(request, BodyHandlers.ofString());
        }
    }

    /**
     * 向实际服务发送 JSON POST 请求，结束后关闭客户端。
     *
     * @param path 请求路径
     * @param body JSON 正文，可为空字符串
     * @return HTTP 响应
     * @throws Exception 请求失败
     */
    private HttpResponse<String> post(String path, String body) throws Exception {
        String port = environment.getRequiredProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(request, BodyHandlers.ofString());
        }
    }
}
