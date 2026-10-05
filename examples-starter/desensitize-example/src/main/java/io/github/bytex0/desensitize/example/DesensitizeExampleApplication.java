package io.github.bytex0.desensitize.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 脱敏示例(DesensitizeExampleApplication)启动入口
 *
 * @author linshiqiang
 * @since 2026-10-05 15:57:28
 */
@SpringBootApplication
public class DesensitizeExampleApplication {
    public static void main(String[] args) { SpringApplication.run(DesensitizeExampleApplication.class, args); }

    @Bean
    CustomMaskHandler customMaskHandler() { return new CustomMaskHandler("managed"); }
}
