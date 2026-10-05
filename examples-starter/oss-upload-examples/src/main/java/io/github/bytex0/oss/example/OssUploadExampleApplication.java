package io.github.bytex0.oss.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 对象存储示例(OssUploadExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 14:55:00
 */
@SpringBootApplication
public class OssUploadExampleApplication {

    /**
     * 启动真实依赖 OSS Starter 的应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OssUploadExampleApplication.class, args);
    }
}
