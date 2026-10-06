package io.github.bytex0.sensitive.handler;

import io.github.bytex0.sensitive.core.SensitiveWordFilter;
import io.github.bytex0.sensitive.core.SensitiveWordOperations;
import io.github.bytex0.sensitive.properties.SensitiveWordProperties;
import org.springframework.core.io.ResourceLoader;

/**
 * 原服务入口(SensitiveWordService)保留原构造、初始化和全部业务重载。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
public class SensitiveWordService extends SensitiveWordOperations {

    /**
     * 保留原构造方式。
     *
     * @param filter 过滤器
     * @param properties 原配置
     */
    public SensitiveWordService(SensitiveWordFilter filter, SensitiveWordProperties properties) {
        super(filter, properties);
    }

    /**
     * 使用当前 Spring 资源加载器。
     *
     * @param filter 过滤器
     * @param properties 原配置
     * @param resources 资源加载器
     */
    public SensitiveWordService(SensitiveWordFilter filter, SensitiveWordProperties properties, ResourceLoader resources) {
        super(filter, properties, resources);
    }
}
