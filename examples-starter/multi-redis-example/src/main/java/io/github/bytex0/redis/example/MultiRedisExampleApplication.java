package io.github.bytex0.redis.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 多Redis示例(MultiRedisExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 16:18:09
 */
@SpringBootApplication
public class MultiRedisExampleApplication {

    /**
     * 启动真实依赖多 Redis Starter 的示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MultiRedisExampleApplication.class, args);
    }
}
