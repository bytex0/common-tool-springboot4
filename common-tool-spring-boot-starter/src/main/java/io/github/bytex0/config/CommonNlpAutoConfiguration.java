package io.github.bytex0.config;

import io.github.bytex0.nlp.DictionarySegmenter;
import io.github.bytex0.nlp.HanlpUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * NLP 工具条件装配，普通词典与 HanLP 引擎分离配置，不强制引入模型依赖。
 *
 * @author bytex0
 * @since 2026-10-06 18:17:11
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "common-tool", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "common-tool.nlp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CommonNlpProperties.class)
@Import(CommonNlpAutoConfiguration.HanlpConfiguration.class)
public class CommonNlpAutoConfiguration {

    /**
     * 创建本应用拥有的独立字典。
     *
     * @param properties 搜索预算配置
     * @return 分词工具
     */
    @Bean
    @ConditionalOnMissingBean
    public DictionarySegmenter dictionarySegmenter(CommonNlpProperties properties) {
        return new DictionarySegmenter(properties.getMaxCombinations(), properties.getMaxOutputTokens());
    }

    /**
     * HanLP 可选依赖条件配置。
     *
     * @author bytex0
     * @since 2026-10-06 18:17:11
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.hankcs.hanlp.HanLP")
    @ConditionalOnProperty(prefix = "common-tool.nlp", name = "hanlp-enabled", havingValue = "true")
    static class HanlpConfiguration {

        /**
         * 创建带独立词库快照的 HanLP 工具。
         *
         * @return HanLP 工具
         */
        @Bean
        @ConditionalOnMissingBean
        HanlpUtil hanlpUtil() {
            return new HanlpUtil();
        }
    }
}
