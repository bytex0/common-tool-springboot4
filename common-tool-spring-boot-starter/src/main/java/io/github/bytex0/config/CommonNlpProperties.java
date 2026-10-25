package io.github.bytex0.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NLP 实例配置，普通字典不访问外部服务，HanLP 模型加载需显式启用。
 *
 * @author bytex0
 * @since 2026-10-06 18:17:11
 */
@Data
@ConfigurationProperties(prefix = "common-tool.nlp")
public class CommonNlpProperties {

    /**
     * 是否创建 NLP 工具，默认 true，受基础总开关控制。
     */
    private boolean enabled = true;

    /**
     * 是否创建 HanLP 工具，默认 false；同时要求可选 HanLP 依赖存在。
     */
    private boolean hanlpEnabled;

    /**
     * 全组合搜索最多输出的分词组合数，默认 10000，必须大于 0。
     */
    private int maxCombinations = 10000;

    /**
     * 全组合结果最多包含的词元总数，默认 1000000，必须大于 0。
     */
    private long maxOutputTokens = 1000000;
}
