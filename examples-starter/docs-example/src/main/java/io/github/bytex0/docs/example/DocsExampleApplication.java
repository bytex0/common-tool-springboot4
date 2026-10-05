package io.github.bytex0.docs.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;

/**
 * 接口文档示例(DocsExampleApplication)启动入口
 *
 * @author bytex0
 * @since 2026-10-05 15:29:19
 */
@SpringBootApplication
@EnableKnife4j
public class DocsExampleApplication {

    /**
     * 启动包含原 Knife4j 增强能力的示例。
     *
     * @param args 应用参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DocsExampleApplication.class, args);
    }
}
