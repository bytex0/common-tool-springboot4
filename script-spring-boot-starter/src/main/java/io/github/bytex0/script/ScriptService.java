package io.github.bytex0.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.util.Assert;

/**
 * 有界脚本调度器，排队和编译时间均计入调用超时。
 *
 * @author bytex0
 * @since 2026-10-05 20:00:38
 */
public class ScriptService implements AutoCloseable {

    private final Map<String, ScriptExecutor> executors;

    private final ThreadPoolExecutor workers;

    private final long timeoutMillis;

    public ScriptService(List<ScriptExecutor> executors, int parallelism, int capacity, long timeoutMillis) {
        Assert.isTrue(parallelism > 0 && parallelism <= 32, "script.parallelism must be 1 to 32");
        Assert.isTrue(capacity > 0 && capacity <= 1000, "script.queue-capacity must be 1 to 1000");
        Assert.isTrue(timeoutMillis > 0 && timeoutMillis <= 120000, "script.timeout must be 1 to 120000ms");
        Map<String, ScriptExecutor> registry = new HashMap<>();
        for (ScriptExecutor executor : executors) {
            Assert.hasText(executor.language(), "Language is required");
            Assert.isTrue(registry.putIfAbsent(executor.language(), executor) == null, "Duplicate script language");
        }
        this.executors = Map.copyOf(registry);
        this.timeoutMillis = timeoutMillis;
        workers = new ThreadPoolExecutor(parallelism, parallelism, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), Thread.ofPlatform().daemon(true).name("script-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public Object execute(String language, String source, Map<String, Object> parameters) throws Exception {
        Assert.hasText(source, "Script source is required");
        Assert.isTrue(source.length() <= 65536, "Script exceeds 65536 characters");
        ScriptExecutor executor = executors.get(language);
        Assert.notNull(executor, "Unsupported script language");
        Map<String, Object> bindings = new HashMap<>(parameters);
        Future<Object> future = workers.submit(() -> executor.execute(source, bindings));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            future.cancel(true);
            workers.purge();
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    @Override
    public void close() {
        for (Runnable queued : workers.shutdownNow()) {
            if (queued instanceof Future<?> future) {
                future.cancel(false);
            }
        }
        try {
            workers.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
