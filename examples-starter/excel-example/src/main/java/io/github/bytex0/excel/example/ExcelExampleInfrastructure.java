package io.github.bytex0.excel.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Excel 联调环境(ExcelExampleInfrastructure)提供仅属于本示例的 H2 数据库，不连接业务数据库。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:23:54
 */
@Configuration(proxyBeanMethods = false)
public class ExcelExampleInfrastructure {

    /**
     * 启动随机名称的内存数据库并在容器结束时关闭。
     *
     * @return 独立测试数据库
     */
    @Bean(destroyMethod = "shutdown")
    public EmbeddedDatabase excelExampleDatabase() {
        return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    }

    /**
     * 创建固定测试表，所有数据按服务器随机生成的 run_id 隔离。
     *
     * @param database 独立数据库
     * @return JDBC 访问器
     */
    @Bean
    public JdbcTemplate excelExampleJdbc(EmbeddedDatabase database) {
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE excel_example_import (run_id VARCHAR(36), id INTEGER, name VARCHAR(128), "
                + "PRIMARY KEY (run_id, id))");
        return jdbc;
    }

    /**
     * 提供真实事务管理器以验证 Starter 的批次回滚。
     *
     * @param database 独立数据库
     * @return 事务管理器
     */
    @Bean
    public PlatformTransactionManager excelExampleTransactions(EmbeddedDatabase database) {
        return new DataSourceTransactionManager(database);
    }
}
