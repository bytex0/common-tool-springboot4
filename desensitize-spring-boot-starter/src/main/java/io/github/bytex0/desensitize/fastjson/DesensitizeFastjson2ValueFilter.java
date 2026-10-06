package io.github.bytex0.desensitize.fastjson;

import com.alibaba.fastjson2.filter.ValueFilter;
import io.github.bytex0.desensitize.handler.DesensitizeHandlerFactory;

/**
 * Fastjson 2 过滤器(DesensitizeFastjson2ValueFilter)为原生 API 提供与兼容路径一致的脱敏规则。
 *
 * @author linshiqiang
 * @since 2026-10-06 10:31:46
 */
public class DesensitizeFastjson2ValueFilter implements ValueFilter {

    /**
     * 当前过滤器的属性规则解析器。
     */
    private final PropertyMasker masker;

    /**
     * 创建显式可注入的过滤器，不改变全局 AutoType 或序列化设置。
     *
     * @param factory 策略工厂
     */
    public DesensitizeFastjson2ValueFilter(DesensitizeHandlerFactory factory) {
        this.masker = new PropertyMasker(factory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object apply(Object object, String name, Object value) {
        return masker.process(object, name, value);
    }
}
