package io.github.bytex0.excel.config;

import io.github.bytex0.excel.ExcelTemplate;
import org.apache.fesod.sheet.FesodSheet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Excel(ExcelAutoConfiguration)自动配置，不创建无界后台任务
 *
 * @author linshiqiang
 * @since 2026-10-05 15:36:55
 */
@AutoConfiguration
@ConditionalOnClass(FesodSheet.class)
@ConditionalOnProperty(prefix = "excel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExcelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExcelTemplate excelTemplate() {
        return new ExcelTemplate();
    }
}
