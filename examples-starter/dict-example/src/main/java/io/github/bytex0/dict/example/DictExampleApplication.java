package io.github.bytex0.dict.example;

import io.github.bytex0.dict.InMemoryDictLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 字典示例(DictExampleApplication)启动及测试数据
 *
 * @author linshiqiang
 * @since 2026-10-05 16:08:16
 */
@SpringBootApplication
public class DictExampleApplication {
    public static void main(String[] args) { SpringApplication.run(DictExampleApplication.class, args); }

    @Bean
    InMemoryDictLoader dictLoader() {
        InMemoryDictLoader loader = new InMemoryDictLoader();
        loader.replace("status", Map.of("1", "启用", "0", "停用"));
        return loader;
    }
}
