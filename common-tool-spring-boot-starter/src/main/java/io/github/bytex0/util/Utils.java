package io.github.bytex0.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

/**
 * 原基础工具入口，提供确定性模板替换、分片及保留 MDC 的有界等待并行调用。
 *
 * @author bytex0
 * @since 2026-10-06 14:41:36
 */
public final class Utils {

    /**
     * 原入口的默认等待上限，单位秒，避免异常任务导致无限等待。
     */
    private static final long DEFAULT_WAIT_SECONDS = 60;

    /**
     * ${name} 形式的单层占位符，不递归展开替换结果。
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");

    /**
     * 保留原公开无参构造入口。
     */
    public Utils() {
    }

    /**
     * 一次性替换原模板中的占位符，不依赖 Map 遍历顺序。
     *
     * @param template 模板，为空返回 null
     * @param parameters 参数，为空原样返回模板；键使用其文本表示
     * @param ignoreNull true 时保留 null 值占位符，false 时替换为空字符串
     * @return 替换结果，byte[] 参数按 UTF-8 解码
     */
    public static String format(CharSequence template, Map<?, ?> parameters, boolean ignoreNull) {
        if (template == null) {
            return null;
        }
        if (parameters == null || parameters.isEmpty()) {
            return template.toString();
        }
        Map<String, Object> values = new HashMap<>();
        parameters.forEach((key, value) -> {
            String name = String.valueOf(key);
            if (values.containsKey(name)) {
                throw new IllegalArgumentException("Template parameter names must be unique");
            }
            values.put(name, value);
        });
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = values.get(name);
            String replacement = matcher.group();
            if (values.containsKey(name) && (value != null || !ignoreNull)) {
                if (value instanceof byte[] bytes) {
                    replacement = new String(bytes, StandardCharsets.UTF_8);
                } else {
                    replacement = value == null ? "" : value.toString();
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 按原列表索引取模分片，返回独立列表，不持有 subList 视图。
     *
     * @param list 非空源列表，调用期间不得并发修改
     * @param shardTotal 分片总数，必须大于 0
     * @param shardIndex 分片索引，范围 0 到 shardTotal-1；-1 保留原空结果约定
     * @param <T> 元素类型
     * @return 分片结果
     */
    public static <T> List<T> getShardList(List<T> list, int shardTotal, int shardIndex) {
        Objects.requireNonNull(list, "list");
        if (shardTotal <= 0 || shardIndex < -1 || shardIndex >= shardTotal) {
            throw new IllegalArgumentException("Invalid shard count or index");
        }
        List<T> result = new ArrayList<>();
        if (shardIndex == -1) {
            return result;
        }
        for (int index = shardIndex; index < list.size(); index += shardTotal) {
            result.add(list.get(index));
            if (index > Integer.MAX_VALUE - shardTotal) {
                break;
            }
        }
        return result;
    }

    /**
     * 并行消费并最多等待六十秒，失败向调用方传播；不关闭调用方线程池。
     *
     * @param list 输入列表，调用期间不得并发修改
     * @param executorService 调用方提供的执行器，不应在该执行器已饱和的任务中嵌套等待
     * @param consumer 消费动作
     * @param <T> 元素类型
     */
    public static <T> void consumerParallel(List<T> list, ExecutorService executorService, Consumer<T> consumer) {
        consumerParallel(list, executorService, consumer, DEFAULT_WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 按指定等待上限并行消费，提交被拒绝、任务失败或中断均不再永久等待。
     * 取消 CompletableFuture 不保证中断已经执行的消费动作，动作必须自行控制副作用。
     *
     * @param list 输入列表
     * @param executorService 调用方执行器
     * @param consumer 消费动作
     * @param timeout 等待时长，必须大于 0
     * @param unit 等待单位
     * @param <T> 元素类型
     */
    public static <T> void consumerParallel(List<T> list, ExecutorService executorService, Consumer<T> consumer,
                                          long timeout, TimeUnit unit) {
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(executorService, "executorService");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(unit, "unit");
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try {
            for (T value : list) {
                futures.add(CompletableFuture.runAsync(withMdc(() -> consumer.accept(value)), executorService));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new)).get(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel consumption interrupted", exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Parallel consumption timed out", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Parallel consumption failed", cause);
        } finally {
            futures.forEach(future -> future.cancel(false));
        }
    }

    /**
     * 使用调用线程 MDC 快照提交任务，结束后恢复工作线程原上下文。
     *
     * @param runnable 任务
     * @param executorService 调用方线程池，不转移所有权
     */
    public static void execute(Runnable runnable, ExecutorService executorService) {
        execute(runnable, (Executor) executorService);
    }

    /**
     * 向任意执行器提交带 MDC 的任务，支持直接执行及 CallerRunsPolicy。
     *
     * @param runnable 任务
     * @param executorService 调用方执行器
     */
    public static void execute(Runnable runnable, Executor executorService) {
        Objects.requireNonNull(executorService, "executorService").execute(withMdc(runnable));
    }

    /**
     * 捕获调用线程的 MDC 并在任务结束后恢复执行线程上下文。
     *
     * @param runnable 非空任务
     * @return 上下文包装任务
     */
    private static Runnable withMdc(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                restoreMdc(captured);
                runnable.run();
            } finally {
                restoreMdc(previous);
            }
        };
    }

    /**
     * 恢复指定 MDC 快照，兼容没有上下文的线程。
     *
     * @param context 快照，可为空
     */
    private static void restoreMdc(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    /**
     * 使用调用方映射器序列化，不修改其配置。
     *
     * @param objectMapper Jackson 3 映射器
     * @param object 待序列化对象
     * @return JSON，失败抛出 Jackson 异常
     */
    public static String toJson(ObjectMapper objectMapper, Object object) {
        return objectMapper.writeValueAsString(object);
    }

    /**
     * 使用调用方映射器解析 JSON。
     *
     * @param objectMapper Jackson 3 映射器
     * @param json JSON 文本
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 解析对象
     */
    public static <T> T toObject(ObjectMapper objectMapper, String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }
}
