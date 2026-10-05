package io.github.bytex0.lock.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 锁示例(LockExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 16:36:32
 */
@SpringBootApplication
public class LockExampleApplication {

    /**
     * 启动实际集成 Starter 的示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LockExampleApplication.class, args);
    }
}
