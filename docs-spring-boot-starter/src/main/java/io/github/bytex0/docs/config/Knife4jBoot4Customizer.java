/*
 * Adapted from Knife4j 4.5.0 Knife4jOpenApiCustomizer.
 * Copyright (c) 2017-2023 Knife4j (xiaoymin@foxmail.com).
 * Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Changes: Springdoc 3 collection compatibility and registered-handler metadata
 * instead of classpath scanning and class initialization.
 */
package io.github.bytex0.docs.config;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import com.github.xiaoymin.knife4j.core.conf.ExtensionsConstants;
import com.github.xiaoymin.knife4j.core.conf.GlobalConstants;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;
import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * 保留 Knife4j 设置、Markdown 和排序扩展，适配 Springdoc 3 的分组集合签名。
 *
 * @author bytex0
 * @since 2026-10-05 23:51:33
 */
public class Knife4jBoot4Customizer extends Knife4jOpenApiCustomizer {

    /**
     * 原 Knife4j 配置，继续使用上游设置和 Markdown 解析器。
     */
    private final Knife4jProperties knifeProperties;

    /**
     * 当前 Springdoc 3 分组配置。
     */
    private final SpringDocConfigProperties docProperties;

    /**
     * 延迟读取已注册 MVC 方法，不触发额外的类路径扫描和静态初始化。
     */
    private final Supplier<Collection<HandlerMethod>> handlers;

    /**
     * 使用点号分隔的包名匹配器，支持原包扫描通配符。
     */
    private final AntPathMatcher packageMatcher = new AntPathMatcher(".");

    /**
     * 构造兼容扩展器，不改变用户的配置对象。
     *
     * @param knifeProperties 原增强配置
     * @param docProperties 当前文档分组配置
     * @param handlers 当前 MVC 处理方法提供器
     */
    public Knife4jBoot4Customizer(Knife4jProperties knifeProperties, SpringDocConfigProperties docProperties,
                                  Supplier<Collection<HandlerMethod>> handlers) {
        super(knifeProperties, docProperties);
        this.knifeProperties = Objects.requireNonNull(knifeProperties);
        this.docProperties = Objects.requireNonNull(docProperties);
        this.handlers = Objects.requireNonNull(handlers);
    }

    /**
     * 应用上游增强协议，不调用含旧 Springdoc 返回签名的父类实现。
     *
     * @param openApi 当前正在生成的文档
     */
    @Override
    public void customise(OpenAPI openApi) {
        if (!knifeProperties.isEnable()) {
            return;
        }
        OpenApiExtensionResolver resolver =
                new OpenApiExtensionResolver(knifeProperties.getSetting(), knifeProperties.getDocuments());
        resolver.start();
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put(GlobalConstants.EXTENSION_OPEN_SETTING_NAME, knifeProperties.getSetting());
        extensions.put(GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME, resolver.getMarkdownFiles());
        openApi.addExtension(GlobalConstants.EXTENSION_OPEN_API_NAME, extensions);
        addOrderExtensions(openApi);
    }

    /**
     * 从活动控制器生成标签排序；同名冲突按类名确定顺序，避免不确定的扫描集合顺序。
     *
     * @param openApi 待增强文档
     */
    private void addOrderExtensions(OpenAPI openApi) {
        if (CollectionUtils.isEmpty(docProperties.getGroupConfigs()) || openApi.getTags() == null) {
            return;
        }
        Set<String> packages = docProperties.getGroupConfigs().stream()
                .map(SpringDocConfigProperties.GroupConfig::getPackagesToScan)
                .filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toSet());
        if (packages.isEmpty()) {
            return;
        }
        Map<String, Integer> orders = new LinkedHashMap<>();
        handlers.get().stream().map(HandlerMethod::getBeanType).distinct()
                .sorted(Comparator.comparing(Class::getName))
                .filter(type -> packages.stream().anyMatch(pattern -> matches(pattern, type.getPackageName())))
                .forEach(type -> {
                    ApiSupport support = AnnotatedElementUtils.findMergedAnnotation(type, ApiSupport.class);
                    Tag tag = AnnotatedElementUtils.findMergedAnnotation(type, Tag.class);
                    if (support != null && tag != null) {
                        orders.putIfAbsent(tag.name(), support.order());
                    }
                });
        openApi.getTags().forEach(tag -> {
            Integer order = orders.get(tag.getName());
            if (order != null) {
                tag.addExtension(ExtensionsConstants.EXTENSION_ORDER, order);
            }
        });
    }

    /**
     * 匹配当前包及其后代，避免 com.foo 错误匹配 com.foobar。
     *
     * @param pattern 配置中的包或包模式
     * @param actual 实际包名
     * @return 是否位于配置范围内
     */
    private boolean matches(String pattern, String actual) {
        return packageMatcher.match(pattern, actual) || packageMatcher.match(pattern + ".**", actual);
    }
}
