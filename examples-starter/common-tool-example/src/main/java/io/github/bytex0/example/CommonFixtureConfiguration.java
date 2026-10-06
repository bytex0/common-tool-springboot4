package io.github.bytex0.example;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * 示例独占资源配置，数据库使用随机名称，既不读取也不修改业务数据源。
 *
 * @author bytex0
 * @since 2026-10-06 14:56:42
 */
@Configuration(proxyBeanMethods = false)
public class CommonFixtureConfiguration {

    /**
     * 创建仅供本示例使用的内存数据库，由应用关闭时销毁。
     *
     * @return 内存测试数据库
     */
    @Bean(destroyMethod = "shutdown")
    public EmbeddedDatabase dataSource() {
        return new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:common-fixture.sql")
                .build();
    }

    /**
     * 创建有界示例执行器，不复用用户业务线程池。
     *
     * @return 应用负责关闭的线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor commonExampleExecutor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32),
                Thread.ofPlatform().daemon(true).name("common-example-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
