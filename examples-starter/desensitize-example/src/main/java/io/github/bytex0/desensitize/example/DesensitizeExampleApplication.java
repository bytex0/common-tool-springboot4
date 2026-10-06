package io.github.bytex0.desensitize.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 脱敏示例(DesensitizeExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 15:57:28
 */
@SpringBootApplication
public class DesensitizeExampleApplication {

    /**
     * 启动真实集成脱敏 Starter 的示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DesensitizeExampleApplication.class, args);
    }

    /**
     * 注册有构造参数的自定义处理器，验证序列化器优先使用容器 Bean。
     *
     * @return 托管处理器
     */
    @Bean
    CustomMaskHandler customMaskHandler() {
        return new CustomMaskHandler("managed");
    }
}
