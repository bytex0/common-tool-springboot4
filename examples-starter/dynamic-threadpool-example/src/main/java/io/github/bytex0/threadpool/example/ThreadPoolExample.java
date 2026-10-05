package io.github.bytex0.threadpool.example;

import io.github.bytex0.threadpool.ThreadPoolRegistry;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 命名线程池任务、监控和动态调整示例。
 *
 * @author bytex0
 * @since 2026-10-05 20:05:22
 */
@SpringBootApplication
@RestController
public class ThreadPoolExample {

    private final ThreadPoolRegistry registry;

    public ThreadPoolExample(ThreadPoolRegistry registry) { this.registry = registry; }

    public static void main(String[] args) { SpringApplication.run(ThreadPoolExample.class, args); }

    @GetMapping("/api/pools/run")
    Map<String, Object> run(@RequestParam(defaultValue = "0") int delay) throws Exception {
        if (delay < 0 || delay > 2000) { throw new IllegalArgumentException("Invalid delay"); }
        String thread = registry.submit("demo", () -> {
            Thread.sleep(delay);
            return Thread.currentThread().getName();
        }).get(8, TimeUnit.SECONDS);
        return Map.of("code", 0, "data", Map.of("thread", thread));
    }

    @GetMapping("/api/pools/stats")
    Map<String, Object> stats() { return Map.of("code", 0, "data", registry.stats("demo")); }

    @PostMapping("/api/pools/resize")
    Map<String, Object> resize(@RequestParam int core, @RequestParam int max) {
        return Map.of("code", 0, "data", registry.resize("demo", core, max));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Integer> invalid() { return Map.of("code", 400); }

    @ExceptionHandler(RejectedExecutionException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    Map<String, Integer> full() { return Map.of("code", 429); }
}
