package io.github.bytex0.ratelimiter.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 限流示例(RateLimiterExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 16:50:07
 */
@SpringBootApplication
public class RateLimiterExampleApplication {

    /**
     * 启动限流真实接口示例。
     *
     * @param args 应用命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RateLimiterExampleApplication.class, args);
    }
}
