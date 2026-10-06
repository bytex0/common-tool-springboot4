package io.github.bytex0.redis.example;

import io.github.bytex0.common.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Redis 操作接口(RedisOperationsController)暴露可重复执行的工具场景，不接收任意业务键。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:07:28
 */
@RestController
@RequestMapping("/api/redis/structures")
public class RedisOperationsController {

    /**
     * 真实 Starter 操作场景。
     */
    private final RedisScenarioService scenarios;

    /**
     * 注入操作场景。
     *
     * @param scenarios 场景服务
     */
    public RedisOperationsController(RedisScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    /**
     * 运行指定数据结构场景，并在返回前清理运行专用键。
     *
     * @param group 场景分组
     * @param client 命名客户端
     * @param run UUID 运行标识
     * @return 场景业务结果
     * @throws Exception 场景执行失败时抛出
     */
    @PostMapping("/{group}")
    public ApiResponse<Map<String, Object>> run(@PathVariable String group,
                                               @RequestParam(defaultValue = "main") String client,
                                               @RequestParam String run) throws Exception {
        return ApiResponse.ok(scenarios.run(group, client, run));
    }

    /**
     * 隔离错误响应，不把连接信息或数据正文返回给调用者。
     *
     * @return 参数错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("Redis场景参数不合法", 400));
    }
}
