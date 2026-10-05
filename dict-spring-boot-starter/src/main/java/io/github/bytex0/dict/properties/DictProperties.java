package io.github.bytex0.dict.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 字典属性(DictProperties)恢复启用与启动刷新配置，并限制缓存类型数量。
 *
 * @author linshiqiang
 * @since 2026-10-06 02:07:34
 */
@Data
@ConfigurationProperties("dict")
public class DictProperties {

    /**
     * 是否启用，默认 true，与原自动配置 matchIfMissing 的实际行为一致。
     */
    private Boolean enabled = true;

    /**
     * 是否在启动时完整刷新一次，默认 true；false 时只在查询未知类型时加载。
     */
    private Boolean autoRefresh = true;

    /**
     * 当前缓存可容纳的类型总数，默认 1024，必须大于零；包含未命中的类型缓存。
     */
    private int maxTypes = 1024;
}
