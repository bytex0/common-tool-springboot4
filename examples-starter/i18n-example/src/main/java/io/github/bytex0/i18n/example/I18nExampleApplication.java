package io.github.bytex0.i18n.example;

import io.github.bytex0.i18n.provider.I18nManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 国际化示例(I18nExampleApplication)启动及测试消息初始化
 *
 * @author linshiqiang
 * @since 2026-10-05 15:49:14
 */
@SpringBootApplication
public class I18nExampleApplication {
    public static void main(String[] args) { SpringApplication.run(I18nExampleApplication.class, args); }

    @Bean
    ApplicationRunner messages(I18nManager manager) {
        return args -> {
            manager.addMessage("zh_CN", "hello", "你好，{0}");
            manager.addMessage("en", "hello", "Hello, {0}");
        };
    }
}
