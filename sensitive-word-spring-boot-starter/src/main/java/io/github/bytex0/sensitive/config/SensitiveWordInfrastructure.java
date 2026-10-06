package io.github.bytex0.sensitive.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import io.github.bytex0.sensitive.web.SensitiveWordWebConfiguration;

/**
 * 装配桥接(SensitiveWordInfrastructure)使两个同名包路径配置共存，无全限定 Java 类型表达式。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@Configuration(proxyBeanMethods = false)
@Import({SensitiveWordAutoConfiguration.class, SensitiveWordWebConfiguration.class})
public class SensitiveWordInfrastructure {
}
