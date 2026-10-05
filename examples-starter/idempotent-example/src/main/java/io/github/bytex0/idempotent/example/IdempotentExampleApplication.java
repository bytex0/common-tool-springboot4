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
    public static void main(String[] args) { SpringApplication.run(IdempotentExampleApplication.class, args); }
}
