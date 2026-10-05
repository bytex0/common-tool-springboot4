package io.github.bytex0.dict.example;

import io.github.bytex0.dict.InMemoryDictLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 字典示例(DictExampleApplication)启动及测试数据
 *
 * @author bytex0
 * @since 2026-10-05 16:08:16
 */
@SpringBootApplication
public class DictExampleApplication {
    /**
     * 启动包含独立内存数据库的真实示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DictExampleApplication.class, args);
    }

    /**
     * 注册由业务管理的初始内存字典，不替代 Starter 的缓存实现。
     *
     * @return 示例加载器
     */
    @Bean
    InMemoryDictLoader dictLoader() {
        InMemoryDictLoader loader = new InMemoryDictLoader();
        loader.replace("status", Map.of("1", "启用", "0", "停用"));
        return loader;
    }
}
