package io.github.bytex0.example.controller;

import io.github.bytex0.balancer.RoundRobinLoadBalancer;
import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.exception.ParamsException;
import io.github.bytex0.id.IdWorkerUtil;
import io.github.bytex0.transation.DoTransactionCompletion;
import io.github.bytex0.transation.TransactionUtils;
import io.github.bytex0.util.TraceIdUtil;
import io.github.bytex0.util.Utils;
import io.github.bytex0.util.ValidationUtil;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础 Starter 实际能力测试接口，所有业务输入只作用于本示例独占资源。
 *
 * @author bytex0
 * @since 2026-10-06 14:56:42
 */
@RestController
public class CommonCapabilityController {

    /**
     * 单次取号上限，避免测试接口被用作无限内存分配。
     */
    private static final int MAX_ID_COUNT = 1000;

    /**
     * 自动配置提供的 ID 工具。
     */
    private final IdWorkerUtil ids;

    /**
     * 自动配置提供的校验工具。
     */
    private final ValidationUtil validation;

    /**
     * 当前示例的随机内存数据库访问器。
     */
    private final JdbcTemplate jdbc;

    /**
     * 当前示例内存数据库的事务模板。
     */
    private final TransactionTemplate transactions;

    /**
     * 示例独占的有界线程池。
     */
    private final ThreadPoolExecutor executor;

    /**
     * 自动配置提供的轮询选择器，泛型使用 Object 以匹配候选模型。
     */
    private final RoundRobinLoadBalancer<Object> balancer;

    /**
     * 注入 Starter 能力与示例专用资源。
     *
     * @param ids ID 工具
     * @param validation 校验工具
     * @param jdbc 示例数据库访问器
     * @param manager 示例事务管理器
     * @param executor 示例线程池
     * @param balancer 轮询选择器
     */
    public CommonCapabilityController(IdWorkerUtil ids, ValidationUtil validation, JdbcTemplate jdbc,
                                      PlatformTransactionManager manager, ThreadPoolExecutor executor,
                                      RoundRobinLoadBalancer<Object> balancer) {
        this.ids = ids;
        this.validation = validation;
        this.jdbc = jdbc;
        transactions = new TransactionTemplate(manager);
        this.executor = executor;
        this.balancer = balancer;
    }

    /**
     * 批量生成字符串 ID，避免 JSON 数字精度丢失。
     *
     * @param count 数量，范围 1 到 1000
     * @return ID 列表
     */
    @GetMapping("/api/common/ids")
    public ApiResponse<List<String>> ids(@RequestParam(defaultValue = "20") int count) {
        if (count <= 0 || count > MAX_ID_COUNT) {
            throw new IllegalArgumentException("count must be between 1 and 1000");
        }
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(ids.nextIdStr());
        }
        return ApiResponse.ok(values);
    }

    /**
     * 通过实际注入的校验工具验证 JSON 模型。
     *
     * @param input 请求模型
     * @return 通过校验的模型
     */
    @PostMapping("/api/common/validate")
    public ApiResponse<ValidationInput> validate(@RequestBody ValidationInput input) {
        validation.validate(input);
        return ApiResponse.ok(input);
    }

    /**
     * 在随机 ID 记录上执行真实提交或回滚，并验证两个回调入口。
     *
     * @param rollback 是否回滚本次事务
     * @return 数据行数与实际回调次数
     */
    @PostMapping("/api/common/transaction")
    public ApiResponse<TransactionResult> transaction(@RequestParam(defaultValue = "false") boolean rollback) {
        String id = UUID.randomUUID().toString();
        AtomicInteger callbacks = new AtomicInteger();
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO common_fixture(id, amount) VALUES (?, ?)", id, 7);
                TransactionUtils.doAfterTransaction(callbacks::incrementAndGet);
                TransactionUtils.doAfterTransaction(new DoTransactionCompletion(callbacks::incrementAndGet));
                if (rollback) {
                    status.setRollbackOnly();
                }
            });
            Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM common_fixture WHERE id = ?", Integer.class, id);
            return ApiResponse.ok(new TransactionResult(rows, callbacks.get()));
        } finally {
            jdbc.update("DELETE FROM common_fixture WHERE id = ?", id);
        }
    }

    /**
     * 通过直接执行器验证 MDC 快照传递和调用线程状态恢复。
     *
     * @return 执行前后请求标识
     */
    @GetMapping("/api/common/trace")
    public ApiResponse<Map<String, String>> trace() {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            TraceIdUtil.buildAndSetTraceId("|", new Object[]{"request", "event"});
            String before = TraceIdUtil.getTraceId();
            Utils.execute(() -> TraceIdUtil.setTraceId("temporary"), (Executor) Runnable::run);
            return ApiResponse.ok(Map.of("before", before, "after", TraceIdUtil.getTraceId()));
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    /**
     * 使用有限固定任务验证并行消费成功及异常路径。
     *
     * @param fail 是否让消费任务抛出固定异常
     * @return 消费总和
     */
    @PostMapping("/api/common/parallel")
    public ApiResponse<Integer> parallel(@RequestParam(defaultValue = "false") boolean fail) {
        AtomicInteger sum = new AtomicInteger();
        Utils.consumerParallel(List.of(1, 2, 3, 4), executor, value -> {
            if (fail) {
                throw new ParamsException("parallel test failure");
            }
            sum.addAndGet(value);
        });
        return ApiResponse.ok(sum.get());
    }

    /**
     * 验证单元素候选轮询和索引分片，不依赖共享轮询的初始位置。
     *
     * @return 轮询结果与分片结果
     */
    @GetMapping("/api/common/tools")
    public ApiResponse<Map<String, Object>> tools() {
        return ApiResponse.ok(Map.of(
                "selected", balancer.get(List.of("only")),
                "shard", Utils.getShardList(List.of(0, 1, 2, 3, 4), 2, 1),
                "formatted", Utils.format("${first}/${second}", Map.of("first", "a", "second", "b"), false)));
    }

    /**
     * 将固定示例输入错误转换为 400，不泄露数据库或线程池实现。
     *
     * @param exception 输入异常
     * @return 统一错误响应
     */
    @ExceptionHandler({ParamsException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalid(RuntimeException exception) {
        return ApiResponse.failOfMessage(exception.getMessage(), 400);
    }

    /**
     * 参数校验示例模型。
     *
     * @param name 非空姓名
     * @param age 非负年龄
     * @author bytex0
     * @since 2026-10-06 14:56:42
     */
    public record ValidationInput(
            /**
             * 姓名，不能为空白。
             */
            @NotBlank(message = "name is required")
            String name,

            /**
             * 年龄，必须非负。
             */
            @Min(value = 0, message = "age must not be negative")
            int age) {
    }

    /**
     * 事务执行后的实际状态。
     *
     * @param rows 事务结束后保留的记录数
     * @param callbacks 实际执行的提交回调数
     * @author bytex0
     * @since 2026-10-06 14:56:42
     */
    public record TransactionResult(
            /**
             * 数据库实际保留的记录数。
             */
            Integer rows,

            /**
             * 实际执行的提交回调次数。
             */
            int callbacks) {
    }
}
