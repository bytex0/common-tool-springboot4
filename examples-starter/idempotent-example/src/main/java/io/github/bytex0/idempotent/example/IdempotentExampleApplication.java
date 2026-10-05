package io.github.bytex0.idempotent.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 幂等示例(IdempotentExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 17:09:18
 */
@SpringBootApplication
public class IdempotentExampleApplication {

    /**
     * 启动真实集成 Starter 的测试应用。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IdempotentExampleApplication.class, args);
    }
}
