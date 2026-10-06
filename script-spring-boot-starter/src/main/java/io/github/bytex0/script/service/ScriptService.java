package io.github.bytex0.script.service;

import io.github.bytex0.script.cache.ScriptCache;
import io.github.bytex0.script.enums.ScriptType;
import io.github.bytex0.script.exception.ScriptExecuteException;
import io.github.bytex0.script.executor.ScriptExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.util.Assert;

/**
 * 类型化脚本服务，恢复按 ID 执行、方法调用、刷新及动态执行器注册。
 * 缓存为实例所有，编译和排队计入超时；脚本必须受信且协作响应中断。
 *
 * @author bytex0
 * @since 2026-10-06 13:58:31
 */
public class ScriptService implements CommandLineRunner, AutoCloseable {

    /**
     * 默认执行线程数量。
     */
    private static final int DEFAULT_PARALLELISM = 2;

    /**
     * 默认待执行队列容量。
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 16;

    /**
     * 默认调用超时，单位毫秒。
     */
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000;

    /**
     * 默认编译缓存容量。
     */
    private static final int DEFAULT_CACHE_CAPACITY = 1000;

    /**
     * 源码长度上限，单位 UTF-16 字符。
     */
    private static final int MAX_SOURCE_LENGTH = 65536;

    /**
     * 最大并行线程数量。
     */
    private static final int MAX_PARALLELISM = 32;

    /**
     * 最大待执行队列容量。
     */
    private static final int MAX_QUEUE_CAPACITY = 1000;

    /**
     * 最大调用超时，单位毫秒。
     */
    private static final long MAX_TIMEOUT_MILLIS = 120000;

    /**
     * 关闭时等待执行任务退出的最长秒数。
     */
    private static final int SHUTDOWN_SECONDS = 5;

    /**
     * 应用实例内的执行器注册表。
     */
    private final ConcurrentHashMap<ScriptType, ScriptExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 本服务拥有的编译缓存。
     */
    private final ScriptCache cache;

    /**
     * 有界任务执行池，由服务关闭。
     */
    private final ThreadPoolExecutor workers;

    /**
     * 包含排队和编译的调用超时，单位毫秒。
     */
    private final long timeoutMillis;

    /**
     * 保留原版仅传执行器列表的构造入口，采用有界默认值。
     *
     * @param executors 非空执行器列表，类型不得重复
     */
    public ScriptService(List<ScriptExecutor> executors) {
        this(executors, DEFAULT_PARALLELISM, DEFAULT_QUEUE_CAPACITY,
                DEFAULT_TIMEOUT_MILLIS, DEFAULT_CACHE_CAPACITY);
    }

    /**
     * 创建有界服务，参数在创建工作线程之前验证。
     *
     * @param executors 非空执行器列表，类型不得重复
     * @param parallelism 并行线程数量，范围 1 到 32
     * @param queueCapacity 排队容量，范围 1 到 1000
     * @param timeoutMillis 超时毫秒，范围 1 到 120000
     * @param cacheCapacity 编译缓存容量，范围 1 到 10000
     */
    public ScriptService(List<ScriptExecutor> executors, int parallelism, int queueCapacity,
                         long timeoutMillis, int cacheCapacity) {
        this(executors, parallelism, queueCapacity, timeoutMillis, cacheCapacity, Map.of());
    }

    /**
     * 创建具有语言缓存配额的有界服务。
     *
     * @param executors 执行器列表
     * @param parallelism 并行数，范围 1 到 32
     * @param queueCapacity 队列容量，范围 1 到 1000
     * @param timeoutMillis 超时毫秒，范围 1 到 120000
     * @param cacheCapacity 总缓存容量，范围 1 到 10000
     * @param typeCapacities 语言缓存配额，范围 1 到 10000
     */
    public ScriptService(List<ScriptExecutor> executors, int parallelism, int queueCapacity,
                         long timeoutMillis, int cacheCapacity, Map<ScriptType, Integer> typeCapacities) {
        Assert.notNull(executors, "Script executors are required");
        Assert.isTrue(parallelism > 0 && parallelism <= MAX_PARALLELISM, "Invalid script parallelism");
        Assert.isTrue(queueCapacity > 0 && queueCapacity <= MAX_QUEUE_CAPACITY, "Invalid script queue capacity");
        Assert.isTrue(timeoutMillis > 0 && timeoutMillis <= MAX_TIMEOUT_MILLIS, "Invalid script timeout");
        for (ScriptExecutor executor : executors) {
            Assert.notNull(executor, "Script executor is required");
            Assert.notNull(executor.getType(), "Script type is required");
            Assert.isTrue(this.executors.putIfAbsent(executor.getType(), executor) == null, "Duplicate script type");
        }
        cache = new ScriptCache(cacheCapacity, typeCapacities);
        this.timeoutMillis = timeoutMillis;
        workers = new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().daemon(true).name("typed-script-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 保留原启动回调入口；注册已在构造阶段完成，避免启动前不可调用。
     *
     * @param args Spring Boot 启动参数，不参与脚本执行
     */
    @Override
    public void run(String... args) {
        Assert.state(!workers.isShutdown(), "Script service is closed");
    }

    /**
     * 按脚本 ID 和类型执行默认入口，源码变化自动重新编译。
     *
     * @param scriptId 非空脚本 ID
     * @param type 引擎类型
     * @param script 非空受信源码，最多 65536 字符
     * @param params 非空参数，复制映射但不深复制参数值
     * @return 脚本结果，可为空
     */
    public Object execute(String scriptId, ScriptType type, String script, Map<String, Object> params) {
        return executeMethod(scriptId, type, script, null, params);
    }

    /**
     * 按 ID 执行方法，空方法名使用引擎默认入口。
     *
     * @param scriptId 非空脚本 ID
     * @param type 引擎类型
     * @param script 非空受信源码
     * @param methodName 方法名，可为空或空白以选择默认入口
     * @param params 非空参数映射
     * @return 脚本结果，可为空
     */
    public Object executeMethod(String scriptId, ScriptType type, String script,
                                String methodName, Map<String, Object> params) {
        checkSource(script);
        Assert.hasText(scriptId, "Script ID is required");
        Assert.notNull(params, "Script parameters are required");
        ScriptExecutor executor = getExecutor(type);
        Map<String, Object> bindings = new HashMap<>(params);
        return await(() -> {
            try (ScriptCache.Lease lease = cache.acquire(scriptId, script, executor)) {
                if (methodName == null || methodName.isBlank()) {
                    return executor.executeCompiled(lease.compiledScript(), bindings);
                }
                return executor.executeCompiledMethod(lease.compiledScript(), methodName, bindings);
            }
        });
    }

    /**
     * 显式重新编译并刷新缓存，失败保留原条目，编译纳入超时。
     *
     * @param scriptId 非空脚本 ID
     * @param type 引擎类型
     * @param script 受信源码
     */
    public void refresh(String scriptId, ScriptType type, String script) {
        checkSource(script);
        Assert.hasText(scriptId, "Script ID is required");
        ScriptExecutor executor = getExecutor(type);
        await(() -> {
            cache.refresh(scriptId, script, executor);
            return Boolean.TRUE;
        });
    }

    /**
     * 删除缓存，不中断已经持有租约的调用。
     *
     * @param scriptId 非空脚本 ID
     */
    public void remove(String scriptId) {
        cache.remove(scriptId);
    }

    /**
     * 校验源码，超时仍由有界调度器控制，不执行脚本体。
     *
     * @param type 引擎类型
     * @param script 受信源码
     */
    public void validate(ScriptType type, String script) {
        checkSource(script);
        ScriptExecutor executor = getExecutor(type);
        await(() -> {
            executor.validate(script);
            return Boolean.TRUE;
        });
    }

    /**
     * 添加或替换执行器；清空旧缓存，在途任务保留原执行器和资源直到结束。
     *
     * @param executor 非空执行器
     */
    public void addExecutor(ScriptExecutor executor) {
        Assert.notNull(executor, "Script executor is required");
        Assert.notNull(executor.getType(), "Script type is required");
        Assert.state(!workers.isShutdown(), "Script service is closed");
        executors.put(executor.getType(), executor);
        cache.clear();
    }

    /**
     * 获取当前注册的执行器，不转移其所有权。
     *
     * @param type 非空类型
     * @return 注册执行器
     */
    public ScriptExecutor getExecutor(ScriptType type) {
        Assert.notNull(type, "Script type is required");
        ScriptExecutor executor = executors.get(type);
        Assert.notNull(executor, "Unsupported script type");
        return executor;
    }

    /**
     * 返回支持类型的不可变快照，不暴露注册表可变视图。
     *
     * @return 当前类型快照
     */
    public Set<ScriptType> getSupportedTypes() {
        return Set.copyOf(executors.keySet());
    }

    /**
     * 在有界队列执行任务并保留中断、原始运行时异常和超时原因。
     *
     * @param task 待执行任务
     * @return 任务结果
     */
    private Object await(Callable<Object> task) {
        Future<Object> future = workers.submit(task);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            future.cancel(true);
            workers.purge();
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ScriptExecuteException("Script execution interrupted or timed out", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ScriptExecuteException("Script execution failed", cause);
        }
    }

    /**
     * 验证源码长度，避免无界编译输入。
     *
     * @param source 源码
     */
    private static void checkSource(String source) {
        Assert.hasText(source, "Script source is required");
        Assert.isTrue(source.length() <= MAX_SOURCE_LENGTH, "Script source exceeds maximum length");
    }

    /**
     * 停止排队任务并限时等待执行线程；不响应中断的任务仍持有租约，不提前关闭资源。
     */
    @Override
    public void close() {
        for (Runnable queued : workers.shutdownNow()) {
            if (queued instanceof Future<?> future) {
                future.cancel(false);
            }
        }
        try {
            workers.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            cache.close();
        }
    }
}
