package io.github.bytex0.example.controller;

import com.alibaba.nacos.api.exception.NacosException;
import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.example.NacosFixtureConfiguration.FixtureConfig;
import io.github.bytex0.example.NacosFixtureConfiguration.Snapshot;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 真实 Nacos 联调接口，所有操作只针对配置好的随机测试 dataId。
 *
 * @author bytex0
 * @since 2026-10-06 18:41:54
 */
@RestController
@ConditionalOnProperty(prefix = "common-tool.nacos", name = "enabled", havingValue = "true")
public class NacosCapabilityController {

    /**
     * 单次测试内容上限，单位 UTF-16 字符。
     */
    private static final int MAX_CONTENT_LENGTH = 4096;

    /**
     * 查询 SDK 等待上限，单位毫秒。
     */
    private static final long READ_TIMEOUT = 3000;

    /**
     * 通过 Starter 实际注册的接收者。
     */
    private final FixtureConfig fixture;

    /**
     * 注入测试接收者。
     *
     * @param fixture 接收者
     */
    public NacosCapabilityController(FixtureConfig fixture) {
        this.fixture = fixture;
    }

    /**
     * 发布本次测试配置，不允许从请求中指定业务 dataId。
     *
     * @param input 非空白配置文本模型，遵守 Nacos SDK 发布约束
     * @return SDK 发布成功响应
     * @throws NacosException SDK 发布失败
     */
    @PostMapping("/api/common/nacos/config")
    public ApiResponse<Map<String, Boolean>> publish(@RequestBody ConfigInput input) throws NacosException {
        if (input.content() == null || input.content().isBlank() || input.content().length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Invalid test configuration content");
        }
        boolean published = fixture.configService().publishConfig(fixture.getDataId(), fixture.getGroup(), input.content());
        if (!published) {
            throw new IllegalStateException("Nacos did not publish the test configuration");
        }
        return ApiResponse.ok(Map.of("published", true));
    }

    /**
     * 同时返回实际服务内容和监听收到的内容，便于断言而非仅检查状态码。
     *
     * @return 服务与监听器状态
     * @throws NacosException SDK 查询失败
     */
    @GetMapping("/api/common/nacos/config")
    public ApiResponse<Map<String, Object>> state() throws NacosException {
        String content = fixture.configService().getConfig(fixture.getDataId(), fixture.getGroup(), READ_TIMEOUT);
        Snapshot snapshot = fixture.snapshot();
        return ApiResponse.ok(Map.of(
                "exists", content != null,
                "content", content == null ? "" : content,
                "receivedPresent", snapshot.content() != null,
                "received", snapshot.content() == null ? "" : snapshot.content(),
                "revision", snapshot.revision()));
    }

    /**
     * 删除本次测试配置，监听器需实际收到删除状态。
     *
     * @return 删除成功响应
     * @throws NacosException SDK 删除失败
     */
    @DeleteMapping("/api/common/nacos/config")
    public ApiResponse<Map<String, Boolean>> delete() throws NacosException {
        if (!fixture.configService().removeConfig(fixture.getDataId(), fixture.getGroup())) {
            throw new IllegalStateException("Nacos did not delete the test configuration");
        }
        return ApiResponse.ok(Map.of("removed", true));
    }

    /**
     * 将无效测试输入映射为 400。
     *
     * @return 统一错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalid() {
        return ApiResponse.failOfMessage("invalid test input", 400);
    }

    /**
     * 发布配置的简单输入模型。
     *
     * @param content 测试配置文本
     * @author bytex0
     * @since 2026-10-06 18:41:54
     */
    public record ConfigInput(
            /**
             * 非空白测试文本，最长 4096 字符；Nacos SDK 不支持发布空白内容。
             */
            String content) {
    }
}
