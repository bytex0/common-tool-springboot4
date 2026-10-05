package io.github.bytex0.cache.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 本地缓存示例(LocalCacheExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 15:14:19
 */
@SpringBootApplication
public class LocalCacheExampleApplication {

    /**
     * 启动本地缓存示例。
     *
     * @param args 应用参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LocalCacheExampleApplication.class, args);
    }
}
