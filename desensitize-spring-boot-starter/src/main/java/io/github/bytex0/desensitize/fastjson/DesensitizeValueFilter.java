package io.github.bytex0.desensitize.fastjson;

import com.alibaba.fastjson.serializer.ValueFilter;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;

/**
 * Fastjson 兼容过滤器(DesensitizeValueFilter)保留原 ValueFilter 入口，异常时禁止原文回退。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
public class DesensitizeValueFilter implements ValueFilter {

    /**
     * 不保存业务对象的属性规则解析器。
     */
    private final PropertyMasker masker;

    /**
     * 使用当前容器的工厂，不修改 Fastjson 全局配置。
     *
     * @param factory 策略工厂
     */
    public DesensitizeValueFilter(DesensitizeHandlerFactory factory) {
        this.masker = new PropertyMasker(factory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object process(Object object, String name, Object value) {
        return masker.process(object, name, value);
    }
}
