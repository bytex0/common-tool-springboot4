package io.github.bytex0.ip2region.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IP 归属地示例启动入口。
 *
 * @author bytex0
 * @since 2026-10-05 19:17:15
 */
@SpringBootApplication
public class IpExampleApplication {

    /**
     * 启动真实 XDB 查询示例。
     *
     * @param args 应用参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IpExampleApplication.class, args);
    }
}
