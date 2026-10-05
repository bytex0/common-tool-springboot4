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

    CommonToolExampleApplicationTest(Environment environment, JsonMapper jsonMapper,
                                     ApplicationInfoInitialize applicationInfoInitialize) {
        this.environment = environment;
        this.jsonMapper = jsonMapper;
        this.applicationInfoInitialize = applicationInfoInitialize;
    }

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

    @Test
    void shouldExposeHealthWithoutExternalInfrastructure() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(response.body()).path("status").asString()).isEqualTo("UP");
    }

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
}
